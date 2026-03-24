package presignedurl;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import java.time.Duration;
import java.util.Map;

public class PreSignedUrl implements RequestHandler<APIGatewayProxyRequestEvent,APIGatewayProxyResponseEvent> {
    private final S3Presigner S3Presigner;
    private final Gson gson;
    private final String bucketName;

    public PreSignedUrl() {
        this.S3Presigner = software.amazon.awssdk.services.s3.presigner.S3Presigner.create();
        this.gson = new Gson();
        this.bucketName = System.getenv("BUCKET_NAME");
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            if (event.getBody() == null || event.getBody().isBlank()) {
                return createResponse(400, Map.of("message", "File name is required in the request body."));
            }
            JsonObject body = gson.fromJson(event.getBody(), JsonObject.class);
            String fileName = body.has("fileName") ? body.get("fileName").getAsString() : null;
            String category = body.has("category") ? body.get("category").getAsString() : null;
            if (fileName == null || category == null) {
                return createResponse(400, Map.of(
                        "message", "Invalid request body. Please provide valid 'fileName' and 'category'."
                ));
            }
            String s3Key = String.format("%s/%s", category, fileName);

            PutObjectRequest objectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();
            PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofHours(1))
                    .putObjectRequest(objectRequest)
                    .build();
            String url = S3Presigner.presignPutObject(presignRequest).url().toString();

            return createResponse(200, Map.of("url", url));

        } catch (Exception e) {
            context.getLogger().log("Error generating pre-signed URL: " + e.getMessage());
            return createResponse(500, Map.of("message", "Failed to generate pre-signed URL."));
        }
    }
    private APIGatewayProxyResponseEvent createResponse(int statusCode, Object body) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(gson.toJson(body));
    }
        }
