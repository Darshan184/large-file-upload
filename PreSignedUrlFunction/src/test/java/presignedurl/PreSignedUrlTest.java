package presignedurl;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class PreSignedUrlTest {
    private PreSignedUrl handler;
    private Context context;
    private Gson gson = new Gson();
    @BeforeEach
    void setUp(){
        handler= new PreSignedUrl();
        context=mock(Context.class);
        when(context.getLogger()).thenReturn(mock(
                LambdaLogger.class
        ));

    }
    @Test
    void testMissingField(){
        APIGatewayProxyRequestEvent event= new APIGatewayProxyRequestEvent()
                .withBody("{\"filename\":\"video.mp4\"}");
        APIGatewayProxyResponseEvent response= handler.handleRequest(event,context);
        assertEquals(400,response.getStatusCode());
        JsonObject responseBody= gson.fromJson(response.getBody(), JsonObject.class);
        assertTrue(responseBody.get("message").getAsString().contains("Invalid request body"));
    }
    @Test
    void testEmptyBody(){
        APIGatewayProxyRequestEvent event= new APIGatewayProxyRequestEvent().withBody("");
        APIGatewayProxyResponseEvent response= handler.handleRequest(event,context);
        assertEquals(400,response.getStatusCode());
    }
}