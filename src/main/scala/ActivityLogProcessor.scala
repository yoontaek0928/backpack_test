import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._
import java.time.ZoneId

object ActivityLogProcessor {

  def main(args: Array[String]): Unit = {
    // SparkSession 설정 (Hive와 통합)
    val spark = SparkSession.builder()
      .appName("Ecommerce Activity Log Processor")
      .config("spark.sql.warehouse.dir", "file:///Users/eeyoontaek/Desktop/backpack_test/hive/warehouse")
      .enableHiveSupport()
      .getOrCreate()

    // KST 시간대 설정
    val KST = ZoneId.of("Asia/Seoul")

    // 데이터 경로 설정
    val inputPath = "/Users/eeyoontaek/Desktop/backpack_test/*.csv"
    val outputPath = "/Users/eeyoontaek/Desktop/backpack_test/output/ecommerce_parquet/"
    val checkpointPath = "/Users/eeyoontaek/Desktop/backpack_test/checkpoints/spark-checkpoints/"

    // Checkpoint 설정
    spark.sparkContext.setCheckpointDir(checkpointPath)

    // 배치 장애 복구용 재시도
    val maxRetries = 3
    var currentAttempt = 0
    var success = false

    while (currentAttempt < maxRetries && !success) {
      try {
        // CSV 파일의 스키마 정의
        val logSchema = StructType(List(
          StructField("event_time", StringType, true),  // 기존 event_time 컬럼 유지
          StructField("event_type", StringType, true),
          StructField("product_id", StringType, true),
          StructField("category_id", StringType, true),
          StructField("category_code", StringType, true),
          StructField("brand", StringType, true),
          StructField("price", DoubleType, true),
          StructField("user_id", StringType, true),
          StructField("user_session", StringType, true)
        ))

        // CSV 파일 읽기
        val activityLogs = spark.read
          .option("header", "true")
          .schema(logSchema)
          .csv(inputPath)

        // event_time 변ㅘㄴ 후 event_time_kst 컬럼 추가
        val logsWithKST = activityLogs.withColumn("event_time_kst",
          from_utc_timestamp(col("event_time"), "Asia/Seoul"))

        // event_time_kst 기준 날짜 파티셔닝
        val partitionedLogs = logsWithKST.withColumn("event_date", to_date(col("event_time_kst")))

        // Parquet + Snappy로 변환하여 저장 (event_time_kst 날짜 기준 파티셔닝)
        partitionedLogs.write
          .mode("overwrite")
          .format("parquet")
          .option("compression", "snappy")
          .partitionBy("event_date")
          .save(outputPath)

        // 외부 테이블 생성 (event_time_kst 날짜별 파티셔닝)
        spark.sql(s"""
          CREATE EXTERNAL TABLE IF NOT EXISTS activity_logs (
            event_time STRING,
            event_time_kst TIMESTAMP,
            event_type STRING,
            product_id STRING,
            category_id STRING,
            category_code STRING,
            brand STRING,
            price DOUBLE,
            user_id STRING,
            user_session STRING
          )
          PARTITIONED BY (event_date DATE)  -- event_time_kst의 날짜로 파티셔닝
          STORED AS PARQUET
          LOCATION '$outputPath'
        """)

        // 파티션 정보 갱신
        spark.sql("MSCK REPAIR TABLE activity_logs")

        // 작업이 성공적으로 완료되면 성공 플래그 설정
        success = true
      } catch {
        case e: Exception =>
          currentAttempt += 1
          println(s"$currentAttempt 번째 시도 실패. 재시도 시작")
          if (currentAttempt >= maxRetries) {
            println("재시도 횟수 초과. 실패")
            throw e
          }
      }
    }

    spark.stop()
  }
}
