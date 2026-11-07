package com.zjgsu.zy;

import com.zjgsu.zy.client.RemoteApi;
import com.zjgsu.zy.client.TextbookClient;
import com.zjgsu.zy.service.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TextbookDiscoveryTest {

    @Test
    void emptyInstancesReturn503WithoutSendingHttpRequest() {
        DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
        RestTemplate restTemplate = mock(RestTemplate.class);

        when(discoveryClient.getInstances("textbook-service"))
                .thenReturn(List.of());

        TextbookClient client = new TextbookClient(
                restTemplate,
                new RemoteApi(),
                discoveryClient,
                true,
                ""
        );

        ApiException exception = assertThrows(
                ApiException.class,
                () -> client.get(1L)
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.status());
        assertEquals("DEPENDENCY_UNAVAILABLE", exception.code());

        verify(discoveryClient).getInstances("textbook-service");
        verifyNoInteractions(restTemplate);
    }

    @Test
    void discoveryFailureReturns503WithoutSendingHttpRequest() {
        DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
        RestTemplate restTemplate = mock(RestTemplate.class);

        when(discoveryClient.getInstances("textbook-service"))
                .thenThrow(new IllegalStateException("discovery unavailable"));

        TextbookClient client = new TextbookClient(
                restTemplate,
                new RemoteApi(),
                discoveryClient,
                true,
                ""
        );

        ApiException exception = assertThrows(
                ApiException.class,
                () -> client.get(1L)
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.status());
        assertEquals("DEPENDENCY_UNAVAILABLE", exception.code());

        verify(discoveryClient).getInstances("textbook-service");
        verifyNoInteractions(restTemplate);
    }
}