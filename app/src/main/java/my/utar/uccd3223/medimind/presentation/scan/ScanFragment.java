package my.utar.uccd3223.medimind.presentation.scan;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;

import dagger.hilt.android.AndroidEntryPoint;
import my.utar.uccd3223.medimind.R;
import my.utar.uccd3223.medimind.databinding.FragmentScanBinding;

@AndroidEntryPoint
public class ScanFragment extends Fragment {

    private FragmentScanBinding binding;
    private ScanViewModel viewModel;

    private ImageCapture imageCapture;
    private Camera camera;

    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private ActivityResultLauncher<String> galleryLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        startCamera();
                    } else {
                        Toast.makeText(requireContext(),
                                R.string.camera_permission_required,
                                Toast.LENGTH_LONG).show();
                    }
                }
        );

        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        Bitmap bitmap = uriToBitmap(uri);
                        if (bitmap != null) {
                            viewModel.analyzeMedication(bitmap);
                        } else {
                            Toast.makeText(requireContext(),
                                    "Failed to load image", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentScanBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(ScanViewModel.class);

        setupControls();
        setupObservers();
        checkCameraPermission();
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.cameraPreview.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();

                camera = cameraProvider.bindToLifecycle(
                        getViewLifecycleOwner(), cameraSelector, preview, imageCapture);

            } catch (Exception e) {
                Toast.makeText(requireContext(),
                        "Failed to start camera: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void setupControls() {
        // Back button
        binding.btnBack.setOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());

        // Capture button
        binding.btnCapture.setOnClickListener(v -> {
            if (imageCapture == null) return;

            imageCapture.takePicture(ContextCompat.getMainExecutor(requireContext()),
                    new ImageCapture.OnImageCapturedCallback() {
                        @Override
                        public void onCaptureSuccess(@NonNull ImageProxy imageProxy) {
                            Bitmap bitmap = imageProxyToBitmap(imageProxy);
                            imageProxy.close();
                            if (bitmap != null) {
                                viewModel.analyzeMedication(bitmap);
                            }
                        }

                        @Override
                        public void onError(@NonNull ImageCaptureException exception) {
                            Toast.makeText(requireContext(),
                                    "Capture failed: " + exception.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        // Gallery button
        binding.btnGallery.setOnClickListener(v -> galleryLauncher.launch("image/*"));

        // Flashlight toggle
        binding.btnFlashlight.setOnClickListener(v -> viewModel.toggleFlashlight());

        // Retake button
        binding.btnRetake.setOnClickListener(v -> viewModel.clearResult());

        // Add Schedule button
        binding.btnAddSchedule.setOnClickListener(v -> {
            ScanResult result = viewModel.getScanResult().getValue();
            if (result != null) {
                if (!result.hasMeaningfulPrefillData()) {
                    Toast.makeText(requireContext(),
                            "No reliable medication fields were extracted. Please retake or add manually.",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                Bundle args = new Bundle();
                args.putString("scanned_med_name", result.getMedicationName());
                args.putString("scanned_med_dosage", result.getDosage());
                args.putString("scanned_med_frequency_doses", result.getFrequencyDoses());
                args.putString("scanned_med_frequency_days", result.getFrequencyDays());
                args.putString("scanned_med_meal_instructions", result.getMealInstructions());
                if (result.getCapturedImage() != null) {
                    String imagePath = saveBitmapToInternalStorage(result.getCapturedImage());
                    if (imagePath != null) {
                        args.putString("scanned_med_image", imagePath);
                    }
                }
                Navigation.findNavController(v).navigate(
                        R.id.action_scan_to_manage, args);
            }
        });

        // Ask AI button — switch to AI tab via bottom nav
        binding.btnAskAi.setOnClickListener(v -> {
            ScanResult result = viewModel.getScanResult().getValue();
            if (result != null) {
                String medName = valueOrFallback(result.getMedicationName(), "this medication");
                String dosage = valueOrFallback(result.getDosage(), "the visible dosage");
                String query = "Tell me about " + medName
                        + " (" + dosage + "). "
                        + "What are its uses, side effects, and important interactions?";
                viewModel.setPendingAiQuery(query);
                BottomNavigationView bottomNav = requireActivity().findViewById(R.id.bottom_navigation);
                bottomNav.setSelectedItemId(R.id.aiAssistantFragment);
            }
        });
    }

    private void setupObservers() {
        // Scan result observer
        viewModel.getScanResult().observe(getViewLifecycleOwner(), result -> {
            if (result != null) {
                binding.scanResultCard.setVisibility(View.VISIBLE);
                binding.resultBackdrop.setVisibility(View.VISIBLE);
                binding.textMedName.setText(valueOrFallback(result.getMedicationName(), "Medication not clearly identified"));
                binding.textMedDetails.setText(valueOrFallback(result.getDosage(), "Dosage not clearly visible"));
                binding.textMedType.setText(valueOrFallback(result.getType(), "Type not clearly visible"));
                binding.textMedUsage.setText(valueOrFallback(result.getUsage(), "Only clearly visible fields will prefill the form"));
                if (result.getCapturedImage() != null) {
                    binding.imgScannedMed.setImageBitmap(result.getCapturedImage());
                }
                boolean canPrefill = result.hasMeaningfulPrefillData();
                binding.btnAddSchedule.setEnabled(canPrefill);
                binding.btnAddSchedule.setAlpha(canPrefill ? 1.0f : 0.5f);
                setCameraControlsEnabled(false);
            } else {
                binding.scanResultCard.setVisibility(View.GONE);
                binding.resultBackdrop.setVisibility(View.GONE);
                binding.btnAddSchedule.setEnabled(true);
                binding.btnAddSchedule.setAlpha(1.0f);
                setCameraControlsEnabled(true);
            }
        });

        // Loading observer
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), loading -> {
            binding.loadingOverlay.setVisibility(loading ? View.VISIBLE : View.GONE);
            if (loading) {
                setCameraControlsEnabled(false);
            } else if (viewModel.getScanResult().getValue() == null) {
                setCameraControlsEnabled(true);
            }
        });

        // Error observer
        viewModel.getError().observe(getViewLifecycleOwner(), errorMsg -> {
            if (errorMsg != null && !errorMsg.isEmpty()) {
                Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show();
            }
        });

        // Flashlight observer
        viewModel.getFlashlightOn().observe(getViewLifecycleOwner(), isOn -> {
            if (camera != null) {
                camera.getCameraControl().enableTorch(isOn != null && isOn);
            }
            binding.imgFlashlightIcon.setImageResource(
                    (isOn != null && isOn) ? R.drawable.ic_flashlight_on : R.drawable.ic_flashlight_off);
        });
    }

    private void setCameraControlsEnabled(boolean enabled) {
        binding.btnCapture.setEnabled(enabled);
        binding.btnCapture.setAlpha(enabled ? 1.0f : 0.5f);
        binding.btnGallery.setEnabled(enabled);
        binding.btnGallery.setAlpha(enabled ? 1.0f : 0.5f);
        binding.btnFlashlight.setEnabled(enabled);
        binding.btnFlashlight.setAlpha(enabled ? 1.0f : 0.5f);
    }

    private Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        ByteBuffer buffer = imageProxy.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

        if (bitmap != null) {
            int rotation = imageProxy.getImageInfo().getRotationDegrees();
            if (rotation != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotation);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0,
                        bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            }
        }
        return bitmap;
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

    private String saveBitmapToInternalStorage(Bitmap bitmap) {
        try {
            File dir = new File(requireContext().getFilesDir(), "medication_images");
            if (!dir.exists()) dir.mkdirs();

            String fileName = "med_" + UUID.randomUUID().toString() + ".jpg";
            File file = new File(dir, fileName);

            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos);
            fos.close();

            return file.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    private String valueOrFallback(String value, String fallback) {
        return (value == null || value.trim().isEmpty()) ? fallback : value;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
