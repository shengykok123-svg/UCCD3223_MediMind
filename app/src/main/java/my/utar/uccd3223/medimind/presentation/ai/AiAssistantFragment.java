package my.utar.uccd3223.medimind.presentation.ai;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.widget.PopupMenu;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

import dagger.hilt.android.AndroidEntryPoint;
import my.utar.uccd3223.medimind.R;
import my.utar.uccd3223.medimind.databinding.FragmentAiAssistantBinding;

@AndroidEntryPoint
public class AiAssistantFragment extends Fragment {

    private FragmentAiAssistantBinding binding;
    private AiAssistantViewModel viewModel;
    private ChatMessageAdapter adapter;

    // Activity result launchers
    private ActivityResultLauncher<String> imagePickerLauncher;
    private ActivityResultLauncher<Intent> speechRecognizerLauncher;
    private ActivityResultLauncher<String> audioPermissionLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Register image picker
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        Bitmap bitmap = uriToBitmap(uri);
                        if (bitmap != null) {
                            viewModel.setPendingImage(bitmap);
                        } else {
                            Toast.makeText(requireContext(), "Failed to load image", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        // Register speech recognizer
        speechRecognizerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == android.app.Activity.RESULT_OK
                            && result.getData() != null) {
                        ArrayList<String> matches = result.getData()
                                .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        if (matches != null && !matches.isEmpty()) {
                            binding.inputMessage.setText(matches.get(0));
                            binding.inputMessage.setSelection(binding.inputMessage.length());
                        }
                    }
                }
        );

        // Register audio permission request
        audioPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        launchSpeechRecognizer();
                    } else {
                        Toast.makeText(requireContext(),
                                "Microphone permission is required for voice input",
                                Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAiAssistantBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(AiAssistantViewModel.class);

        setupRecyclerView();
        setupInputControls();
        setupMenuButton();
        setupObservers();

        // Check for pending medication query from scan
        my.utar.uccd3223.medimind.presentation.scan.ScanViewModel scanViewModel =
                new ViewModelProvider(requireActivity()).get(
                        my.utar.uccd3223.medimind.presentation.scan.ScanViewModel.class);
        String pendingQuery = scanViewModel.consumePendingAiQuery();
        if (pendingQuery != null && !pendingQuery.isEmpty()) {
            viewModel.sendMessage(pendingQuery);
        } else if (getArguments() != null) {
            String medQuery = getArguments().getString("medicationQuery", "");
            if (!medQuery.isEmpty()) {
                viewModel.sendMessage(medQuery);
                getArguments().remove("medicationQuery");
            }
        }
    }

    private void setupRecyclerView() {
        adapter = new ChatMessageAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setStackFromEnd(true);
        binding.recyclerChat.setLayoutManager(layoutManager);
        binding.recyclerChat.setAdapter(adapter);
    }

    private void setupInputControls() {
        // Send button
        binding.btnSend.setOnClickListener(v -> {
            String text = binding.inputMessage.getText().toString();
            Bitmap pendingImg = viewModel.getPendingImage().getValue();

            if (text.trim().isEmpty() && pendingImg == null) return;

            if (pendingImg != null) {
                viewModel.sendMessageWithImage(text, pendingImg);
                viewModel.clearPendingImage();
            } else {
                viewModel.sendMessage(text);
            }
            binding.inputMessage.setText("");
        });

        // Mic button
        binding.btnMic.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                launchSpeechRecognizer();
            } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            }
        });

        // Image button
        binding.btnImage.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));

        // Remove image button
        binding.btnRemoveImage.setOnClickListener(v -> viewModel.clearPendingImage());
    }

    private void setupMenuButton() {
        binding.btnMenu.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(requireContext(), v);
            popup.getMenu().add(0, 1, 0, R.string.clear_chat);
            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) {
                    viewModel.clearChat();
                    return true;
                }
                return false;
            });
            popup.show();
        });
    }

    private void setupObservers() {
        // Messages observer
        viewModel.getMessages().observe(getViewLifecycleOwner(), messages -> {
            if (messages != null) {
                adapter.submitList(new ArrayList<>(messages), () -> {
                    // Auto-scroll to latest message
                    if (!messages.isEmpty()) {
                        binding.recyclerChat.smoothScrollToPosition(messages.size() - 1);
                    }
                });
            }
        });

        // Loading observer - disable/enable input controls
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), loading -> {
            binding.btnSend.setEnabled(!loading);
            binding.btnMic.setEnabled(!loading);
            binding.btnImage.setEnabled(!loading);
            binding.inputMessage.setEnabled(!loading);
        });

        // Pending image observer - show/hide image preview
        viewModel.getPendingImage().observe(getViewLifecycleOwner(), bitmap -> {
            if (bitmap != null) {
                binding.imagePreviewContainer.setVisibility(View.VISIBLE);
                binding.imagePreview.setImageBitmap(bitmap);
            } else {
                binding.imagePreviewContainer.setVisibility(View.GONE);
                binding.imagePreview.setImageBitmap(null);
            }
        });
    }

    private void launchSpeechRecognizer() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your health question...");

        try {
            speechRecognizerLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(),
                    "Speech recognition is not available on this device",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap uriToBitmap(Uri uri) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.Source source = ImageDecoder.createSource(
                        requireContext().getContentResolver(), uri);
                return ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                    decoder.setMutableRequired(true);
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                });
            } else {
                return MediaStore.Images.Media.getBitmap(
                        requireContext().getContentResolver(), uri);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
