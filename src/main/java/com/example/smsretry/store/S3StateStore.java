// store/S3StateStore.java
package com.example.smsretry.store;

import com.example.smsretry.model.MessageState;
import com.example.smsretry.util.Json;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.ArrayList;
import java.util.List;

@Component

public class S3StateStore implements StateStore {

    private final S3Client s3;
    private final String bucket;
    private final S3Writer writer;

    public S3StateStore(
            @Value("${aws.region}") String region,
            @Value("${aws.s3.bucket}") String bucket) {
        this.s3 = S3Client.builder().region(Region.of(region)).build();
        this.bucket = bucket;
        this.writer = new S3Writer(s3, bucket);
    }

    @Override
    public void enqueue(StateUpdate update) {
        writer.enqueue(update);
    }

    @Override
    public List<MessageState> loadPendingAll() {
        List<MessageState> out = new ArrayList<>();

        String prefix = "state/pending/";
        String token = null;

        do {
            ListObjectsV2Request req = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .continuationToken(token)
                    .build();

            ListObjectsV2Response resp = s3.listObjectsV2(req);
            for (S3Object obj : resp.contents()) {
                try {
                    GetObjectRequest getReq = GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(obj.key())
                            .build();

                    ResponseBytes<GetObjectResponse> bytes = s3.getObjectAsBytes(getReq);
                    MessageState s = Json.MAPPER.readValue(bytes.asByteArray(), MessageState.class);
                    out.add(s);
                } catch (Exception ignored) {
                }
            }
            token = resp.nextContinuationToken();
        } while (token != null);

        return out;
    }
}