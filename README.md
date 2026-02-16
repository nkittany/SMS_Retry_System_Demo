SMS Retry System – Mission Submission

1.  Problem Statement

The goal of this mission is to design and implement a high-throughput
SMS sending system that:

-   Accepts large volumes of SMS messages
-   Simulates message sending with configurable success rate
-   Retries failed messages
-   Persists state to prevent data loss
-   Recovers automatically after restart
-   Is deployed on AWS infrastructure

The system is designed to be production-ready and scalable.

------------------------------------------------------------------------

2.  Architecture

Client (Postman / Browser) ↓ Spring Boot REST API (EC2) ↓ Retry Engine
(Concurrent In-Memory Processing) ↓ Amazon S3 (Persistent Storage)

------------------------------------------------------------------------

3.  Design Decisions

-   Spring Boot was chosen for lightweight REST API development.
-   Amazon S3 was selected for durable, scalable object storage.
-   EC2 with IAM Role was used to securely access S3 without access
    keys.
-   systemd is used to run the application as a background service.

------------------------------------------------------------------------

4.  Message Flow

5.  Client sends POST /messages request.

6.  Messages are generated and stored in S3 as pending.

7.  Retry engine processes messages concurrently.

8.  Success or failure state is updated in S3.

9.  On restart, the system reloads pending messages from S3.

------------------------------------------------------------------------

5.  High Throughput Strategy

-   Concurrent thread-based processing
-   Asynchronous retry mechanism
-   S3 used as durable storage
-   Horizontal scaling possible via multiple EC2 instances

------------------------------------------------------------------------

6.  AWS Deployment

EC2: - Amazon Linux 2023 - Java 17 (Corretto) - Port 8080 enabled - IAM
role attached for S3 access

S3: - Bucket stores message states (pending, failed, success)

------------------------------------------------------------------------

7.  Security

-   No AWS credentials stored in code
-   IAM Role attached to EC2
-   Security group restricts inbound traffic

------------------------------------------------------------------------

8.  How to Run Locally

Build: ./mvnw clean package

Run: SPRING_PROFILES_ACTIVE=aws java -jar target/demo-0.0.1-SNAPSHOT.jar

------------------------------------------------------------------------

9.  How to Run on EC2

Upload JAR: scp app.jar ec2-user@:/home/ec2-user/

Run: java -jar app.jar –spring.profiles.active=aws

Production (systemd): sudo systemctl start sms-retry

------------------------------------------------------------------------

10. API Endpoints

POST /messages GET /messages/success?limit=100 GET
/messages/failed?limit=100 GET /actuator/health

------------------------------------------------------------------------

11. Conclusion

The system fulfills the mission requirements by: - Handling high message
throughput - Retrying failed messages - Persisting state in S3 -
Recovering automatically after restart - Securely deploying on AWS EC2

Author: Noor
