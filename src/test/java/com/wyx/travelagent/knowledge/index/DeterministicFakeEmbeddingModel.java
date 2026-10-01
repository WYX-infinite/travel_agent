package com.wyx.travelagent.knowledge.index;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 测试专用的确定性 EmbeddingModel：不访问网络，并允许按正文注入失败。
 */
public final class DeterministicFakeEmbeddingModel implements EmbeddingModel {
    private final String modelName;
    private final int dimension;
    private final List<String> embeddedTexts = new CopyOnWriteArrayList<>();
    private final List<Integer> embeddedBatchSizes = new CopyOnWriteArrayList<>();
    private int maximumBatchSize = Integer.MAX_VALUE;
    private String failureText;
    private String blockingText;
    private CountDownLatch blockingEntered;
    private CountDownLatch blockingRelease;

    public DeterministicFakeEmbeddingModel(String modelName, int dimension) {
        this.modelName = modelName;
        this.dimension = dimension;
    }

    public void failWhenTextContains(String failureText) {
        this.failureText = failureText;
    }

    public void blockWhenTextContains(
            String blockingText, CountDownLatch entered, CountDownLatch release) {
        this.blockingText = blockingText;
        this.blockingEntered = entered;
        this.blockingRelease = release;
    }

    public List<String> embeddedTexts() {
        return List.copyOf(embeddedTexts);
    }

    public void rejectBatchesLargerThan(int maximumBatchSize) {
        if (maximumBatchSize < 1) {
            throw new IllegalArgumentException("maximumBatchSize must be positive");
        }
        this.maximumBatchSize = maximumBatchSize;
    }

    public List<Integer> embeddedBatchSizes() {
        return List.copyOf(embeddedBatchSizes);
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
        embeddedBatchSizes.add(segments.size());
        if (segments.size() > maximumBatchSize) {
            throw new IllegalArgumentException("simulated embedding batch limit exceeded");
        }
        List<Embedding> embeddings = new java.util.ArrayList<>(segments.size());
        for (TextSegment segment : segments) {
            String text = segment.text();
            embeddedTexts.add(text);
            if (failureText != null && text.contains(failureText)) {
                throw new IllegalStateException("simulated embedding failure");
            }
            if (blockingText != null && text.contains(blockingText)) {
                blockingEntered.countDown();
                try {
                    if (!blockingRelease.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("simulated embedding wait timed out");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("simulated embedding wait interrupted", exception);
                }
            }
            embeddings.add(Embedding.from(vector(text)));
        }
        return Response.from(List.copyOf(embeddings));
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public String modelName() {
        return modelName;
    }

    private float[] vector(String text) {
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
        float[] vector = new float[dimension];
        double norm = 0;
        for (int index = 0; index < dimension; index++) {
            vector[index] = ((digest[index % digest.length] & 0xff) + 1) / 256.0f;
            norm += vector[index] * vector[index];
        }
        float length = (float) Math.sqrt(norm);
        for (int index = 0; index < vector.length; index++) {
            vector[index] /= length;
        }
        return vector;
    }
}
