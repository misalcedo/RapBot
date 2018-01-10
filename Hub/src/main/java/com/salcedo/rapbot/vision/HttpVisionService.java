package com.salcedo.rapbot.vision;

import akka.http.javadsl.Http;
import akka.http.javadsl.model.*;
import akka.stream.IOResult;
import akka.stream.Materializer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static akka.stream.javadsl.FileIO.toPath;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.runAsync;

public final class HttpVisionService implements VisionService {
    private final Http http;
    private final Materializer materializer;
    private final Uri destination;

    HttpVisionService(final Http http, final Materializer materializer, final Uri destination) {
        this.http = http;
        this.materializer = materializer;
        this.destination = destination;
    }

    @Override
    public CompletionStage<Path> takePicture() {
        final Path path = createPath();

        return http.singleRequest(createHttpRequest(), materializer)
                .thenApply(HttpResponse::entity)
                .thenApply(HttpEntity::getDataBytes)
                .thenCompose(source -> source.runWith(toPath(path), materializer))
                .thenApply(ioResult -> mapToPath(path, ioResult));
    }

    private Path mapToPath(final Path path, final IOResult ioResult) {
        if (ioResult.wasSuccessful()) {
            return path;
        }

        throw new RuntimeException(ioResult.getError());
    }

    private HttpRequest createHttpRequest() {
        return HttpRequest.create()
                .withUri(destination)
                .withMethod(HttpMethods.GET);
    }

    private Path createPath() {
        try {
            return Files.createTempFile("image", ".jpg");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
