package presignedurl;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PreSignedUrl implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final Gson gson;
    private final String bucketName;
    @SuppressWarnings("unused")
    public PreSignedUrl() {
        Region region = Region.of(System.getenv("AWS_REGIONS"));
        this.s3Presigner = S3Presigner.builder().region(region).build();
        this.s3Client = S3Client.builder().region(region).build();
        this.gson = new Gson();
        this.bucketName = System.getenv("BUCKET_NAME");
    }
    public PreSignedUrl(S3Presigner s3Presigner, S3Client s3Client, String bucketName) {
        this.s3Presigner = s3Presigner;
        this.s3Client = s3Client;
        this.gson = new Gson();
        this.bucketName = bucketName;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            if (event.getBody() == null || event.getBody().isBlank()) {
                return createResponse(400, Map.of("message", "Body is required"));
            }
            JsonObject body = gson.fromJson(event.getBody(), JsonObject.class);
            String action = body.has("action") ? body.get("action").getAsString() : "INITIATE";
            String fileName = body.get("fileName").getAsString();
            String category = body.get("category").getAsString();
            String s3Key = String.format("%s/%s", category, fileName);
            switch (action) {
                case "INITIATE":
                    return initiateUpload(s3Key);

                case "GET_URLS":
                    String uploadId = body.get("uploadId").getAsString();
                    int partCount = body.get("partCount").getAsInt();
                    return getPartUrls(s3Key, uploadId, partCount);

                case "COMPLETE":
                    String uId = body.get("uploadId").getAsString();
                    JsonArray partsArray = body.getAsJsonArray("parts");
                    return completeUpload(s3Key, uId, partsArray);

                default:
                    return createResponse(400, Map.of("message", "Invalid action"));
            }
        } catch (java.lang.Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return createResponse(500, Map.of("message", e.getMessage()));
        }

    }

    private APIGatewayProxyResponseEvent initiateUpload(String key) {
        CreateMultipartUploadResponse response = s3Client.createMultipartUpload(
                CreateMultipartUploadRequest.builder().bucket(bucketName).key(key).build());
        return createResponse(200, Map.of("uploadId", response.uploadId(), "key", key));
    }

    private APIGatewayProxyResponseEvent getPartUrls(String key, String uploadId, int partCount) {
        List<String> urls = new ArrayList<>();
        for (int i = 1; i <= partCount; i++) {
            UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                    .bucket(bucketName).key(key).uploadId(uploadId).partNumber(i).build();
            UploadPartPresignRequest presignRequest = UploadPartPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(15))
                    .uploadPartRequest(uploadPartRequest).build();
            urls.add(s3Presigner.presignUploadPart(presignRequest).url().toString());
        }
        return createResponse(200, Map.of("urls", urls));
    }

    private APIGatewayProxyResponseEvent completeUpload(String key, String uploadId, JsonArray partsArray) {
        List<CompletedPart> completedParts = new ArrayList<>();
        partsArray.forEach(element -> {
            JsonObject partObj = element.getAsJsonObject();
            completedParts.add(CompletedPart.builder()
                    .partNumber(partObj.get("partNumber").getAsInt())
                    .eTag(partObj.get("eTag").getAsString()).build());
        });
        CompletedMultipartUpload completedMultipartUpload = CompletedMultipartUpload.builder()
                .parts(completedParts).build();
        s3Client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                .bucket(bucketName).key(key).uploadId(uploadId)
                .multipartUpload(completedMultipartUpload).build());
        String finalUrl = String.format("https://%s.s3.amazonaws.com/%s", bucketName, key);
        return createResponse(200, Map.of("message", "Upload complete", "url", finalUrl));
    }
    private APIGatewayProxyResponseEvent createResponse(int statusCode, Object body) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(Map.of("Content-Type", "application/json", "Access-Control-Allow-Origin", "*"))
                .withBody(gson.toJson(body));
    }
}

