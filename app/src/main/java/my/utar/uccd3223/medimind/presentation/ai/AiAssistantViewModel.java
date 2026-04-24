package my.utar.uccd3223.medimind.presentation.ai;

import android.graphics.Bitmap;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.ChatFutures;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.ai.client.generativeai.type.RequestOptions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import my.utar.uccd3223.medimind.BuildConfig;

@HiltViewModel
public class AiAssistantViewModel extends ViewModel {

    private static final List<String> MODEL_NAMES = Arrays.asList(
            "gemini-2.5-flash-lite",
            "gemini-2.0-flash",
            "gemini-2.5-flash"
    );

    private final Executor executor;
    private final List<GenerativeModelFutures> models = new ArrayList<>();
    private final List<ChatFutures> chatSessions = new ArrayList<>();

    private final List<ChatMessage> messageList = new ArrayList<>();
    private final MutableLiveData<List<ChatMessage>> messages = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Bitmap> pendingImage = new MutableLiveData<>();
    private String pendingQuery;

    @Inject
    public AiAssistantViewModel(Executor executor) {
        this.executor = executor;

        Content.Builder sysBuilder = new Content.Builder();
        sysBuilder.addText("You are MediMind AI Assistant, a helpful and knowledgeable health and medication assistant. "
                + "You help users understand their medications, potential side effects, drug interactions, "
                + "dosage information, and general health questions. "
                + "Always be clear, accurate, and empathetic in your responses. "
                + "When discussing medications, remind users to consult their healthcare provider or pharmacist "
                + "for personalized medical advice. "
                + "IMPORTANT: You are NOT a replacement for professional medical advice. Always include a brief "
                + "disclaimer when providing health or medication information. "
                + "Keep responses concise and easy to understand. Use bullet points for lists when appropriate. "
                + "If a user shares an image of medication, packaging, or a prescription, analyze it and provide "
                + "relevant information about the medication shown.");
        Content systemInstruction = sysBuilder.build();

        for (String modelName : MODEL_NAMES) {
            GenerativeModelFutures generativeModel = GenerativeModelFutures.from(createGenerativeModel(modelName, systemInstruction));
            models.add(generativeModel);
            chatSessions.add(generativeModel.startChat());
        }

        // Add welcome message
        addMessage(new ChatMessage(
                "Hello! I'm your MediMind AI Assistant. I can help you with:\n\n"
                        + "\u2022 Medication information & usage\n"
                        + "\u2022 Side effects & interactions\n"
                        + "\u2022 General health questions\n"
                        + "\u2022 Analyzing medication images\n\n"
                        + "How can I assist you today?",
                ChatMessage.TYPE_AI
        ));
    }

    public LiveData<List<ChatMessage>> getMessages() {
        return messages;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<Bitmap> getPendingImage() {
        return pendingImage;
    }

    public void setPendingImage(Bitmap bitmap) {
        pendingImage.setValue(bitmap);
    }

    public void clearPendingImage() {
        pendingImage.setValue(null);
    }

    public void setPendingQuery(String query) {
        pendingQuery = query != null ? query.trim() : null;
    }

    public String consumePendingQuery() {
        String query = pendingQuery;
        pendingQuery = null;
        return query;
    }

    public void sendMessage(String text) {
        if (text == null || text.trim().isEmpty()) return;

        String trimmed = text.trim();
        Bitmap image = pendingImage.getValue();

        if (image != null) {
            sendMessageWithImage(trimmed, image);
            clearPendingImage();
            return;
        }

        // Add user message
        addMessage(new ChatMessage(trimmed, ChatMessage.TYPE_USER));

        // Add loading indicator
        ChatMessage loadingMsg = new ChatMessage("Thinking...", ChatMessage.TYPE_LOADING);
        addMessage(loadingMsg);
        isLoading.setValue(true);

        // Build content and send
        Content.Builder contentBuilder = new Content.Builder();
        contentBuilder.setRole("user");
        contentBuilder.addText(trimmed);
        Content content = contentBuilder.build();

        sendChatMessageWithFallback(content, loadingMsg, 0);
    }

    private void sendChatMessageWithFallback(Content content, ChatMessage loadingMsg, int modelIndex) {
        ListenableFuture<GenerateContentResponse> future = chatSessions.get(modelIndex).sendMessage(content);
        Futures.addCallback(future, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                String responseText = result.getText();
                removeMessage(loadingMsg);
                if (responseText != null && !responseText.isEmpty()) {
                    addMessage(new ChatMessage(responseText.trim(), ChatMessage.TYPE_AI));
                } else {
                    addMessage(new ChatMessage("I couldn't generate a response. Please try again.", ChatMessage.TYPE_AI));
                }
                isLoading.postValue(false);
            }

            @Override
            public void onFailure(Throwable t) {
                int nextModelIndex = modelIndex + 1;
                if (nextModelIndex < chatSessions.size()) {
                    sendChatMessageWithFallback(content, loadingMsg, nextModelIndex);
                    return;
                }

                removeMessage(loadingMsg);
                String errorMsg = t.getMessage();
                if (errorMsg == null || errorMsg.isEmpty()) {
                    errorMsg = "An unexpected error occurred";
                }
                addMessage(new ChatMessage("Sorry, something went wrong: " + errorMsg, ChatMessage.TYPE_AI));
                isLoading.postValue(false);
            }
        }, executor);
    }

    public void sendMessageWithImage(String text, Bitmap image) {
        String messageText = (text == null || text.trim().isEmpty()) ? "What can you tell me about this image?" : text.trim();

        // Add user message with image
        addMessage(new ChatMessage(messageText, ChatMessage.TYPE_USER, image));

        // Add loading indicator
        ChatMessage loadingMsg = new ChatMessage("Analyzing image...", ChatMessage.TYPE_LOADING);
        addMessage(loadingMsg);
        isLoading.setValue(true);

        // Build multimodal content
        Content.Builder contentBuilder = new Content.Builder();
        contentBuilder.setRole("user");
        contentBuilder.addImage(image);
        contentBuilder.addText(messageText);
        Content content = contentBuilder.build();

        // Use generateContent for multimodal (chat history doesn't support images well)
        generateImageResponseWithFallback(content, loadingMsg, 0);
    }

    private void generateImageResponseWithFallback(Content content, ChatMessage loadingMsg, int modelIndex) {
        ListenableFuture<GenerateContentResponse> future = models.get(modelIndex).generateContent(content);
        Futures.addCallback(future, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                String responseText = result.getText();
                removeMessage(loadingMsg);
                if (responseText != null && !responseText.isEmpty()) {
                    addMessage(new ChatMessage(responseText.trim(), ChatMessage.TYPE_AI));
                } else {
                    addMessage(new ChatMessage("I couldn't analyze the image. Please try again.", ChatMessage.TYPE_AI));
                }
                isLoading.postValue(false);
            }

            @Override
            public void onFailure(Throwable t) {
                int nextModelIndex = modelIndex + 1;
                if (nextModelIndex < models.size()) {
                    generateImageResponseWithFallback(content, loadingMsg, nextModelIndex);
                    return;
                }

                removeMessage(loadingMsg);
                String errorMsg = t.getMessage();
                if (errorMsg == null || errorMsg.isEmpty()) {
                    errorMsg = "An unexpected error occurred";
                }
                addMessage(new ChatMessage("Sorry, I couldn't process the image: " + errorMsg, ChatMessage.TYPE_AI));
                isLoading.postValue(false);
            }
        }, executor);
    }

    private GenerativeModel createGenerativeModel(String modelName, Content systemInstruction) {
        return new GenerativeModel(
                modelName,
                BuildConfig.GEMINI_API_KEY,
                null, // generationConfig
                null, // safetySettings
                new RequestOptions(), // requestOptions (non-nullable in Kotlin)
                null, // tools
                null, // toolConfig
                systemInstruction
        );
    }

    private synchronized void addMessage(ChatMessage message) {
        messageList.add(message);
        messages.postValue(new ArrayList<>(messageList));
    }

    private synchronized void removeMessage(ChatMessage message) {
        messageList.remove(message);
        messages.postValue(new ArrayList<>(messageList));
    }

    public synchronized void clearChat() {
        messageList.clear();
        addMessage(new ChatMessage(
                "Chat cleared. How can I assist you?",
                ChatMessage.TYPE_AI
        ));
    }
}
