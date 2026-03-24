package presignedurl;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class PreSignedUrlTest {
    private PreSignedUrl handler;
    private Context context;
    private S3Client mockS3Client;
    private S3Presigner mockPresigner;
    private final Gson gson = new Gson();

    @BeforeEach
    void setUp() {
        mockS3Client = mock(S3Client.class);
        mockPresigner = mock(S3Presigner.class);
        context = mock(Context.class);
        when(context.getLogger()).thenReturn(mock(LambdaLogger.class));
        handler = new PreSignedUrl(mockPresigner, mockS3Client, "test-bucket");
    }

    @Test
    void testInitiateAction() {
        String expectedId = "upload-123";
        CreateMultipartUploadResponse s3Response = CreateMultipartUploadResponse.builder()
                .uploadId(expectedId)
                .build();
        when(mockS3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(s3Response);
        String body = "{\"action\":\"INITIATE\",\"fileName\":\"test.mp4\",\"category\":\"videos\"}";
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent().withBody(body);
        APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);
        assertEquals(200, response.getStatusCode());
        JsonObject responseBody = gson.fromJson(response.getBody(), JsonObject.class);
        assertEquals(expectedId, responseBody.get("uploadId").getAsString());
        verify(mockS3Client, times(1)).createMultipartUpload(any(CreateMultipartUploadRequest.class));
    }
    @Test
    void testMissingField() {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withBody("{\"fileName\":\"video.mp4\"}");
        APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);
        assertEquals(500, response.getStatusCode());
    }

    @Test
    void testEmptyBody() {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent().withBody("");
        APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);
        assertEquals(400, response.getStatusCode());
    }

    @Test
    void testInvalidAction() {
        String body = "{\"action\":\"DANCE\",\"fileName\":\"test.mp4\",\"category\":\"videos\"}";
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent().withBody(body);
        APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Invalid action"));
    }
}