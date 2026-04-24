package my.utar.uccd3223.medimind.presentation.home;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.DatePickerDialog;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.Locale;

import my.utar.uccd3223.medimind.R;
import my.utar.uccd3223.medimind.databinding.FragmentHomeBinding;
import my.utar.uccd3223.medimind.presentation.ai.AiAssistantViewModel;
import my.utar.uccd3223.medimind.util.ThemeManager;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Shows today's medication schedule and provides quick dose actions.
 */
@AndroidEntryPoint
public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private HomeViewModel viewModel;
    private MedicationAdapter adapter;
    private GestureDetector weekSwipeDetector;
    private ObjectAnimator weekAnimator;

    // Currently selected calendar date
    private Calendar selectedCalendar = Calendar.getInstance();

    /**
     * Inflates the home dashboard layout.
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    /**
     * Initializes date header, weekly calendar, medication list, and observers.
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        setupHeader();
        setupWeekCalendar();
        setupRecyclerView();

        // Pre-set date flags on new adapter before observers fire
        String currentDate = viewModel.getSelectedDate().getValue();
        if (currentDate != null) {
            LocalDate selected = LocalDate.parse(currentDate);
            LocalDate today = LocalDate.now();
            adapter.setPastDate(selected.isBefore(today));
            adapter.setFutureDate(selected.isAfter(today));
        }

        setupObservers();
        setupButtons();

        // Initial load
        viewModel.loadMedications();
    }

    /**
     * Displays the current month/year header for the selected calendar date.
     */
    private void setupHeader() {
        updateDateDisplay();
        updateThemeToggleState();
    }

    /**
     * Updates all date labels when the user changes day or week.
     */
    private void updateDateDisplay() {
        Calendar today = Calendar.getInstance();
        boolean isToday = isSameDay(selectedCalendar, today);

        // --- Heading ---
        if (isToday) {
            binding.textTodaysPlan.setText(R.string.todays_plan);
        } else {
            Calendar yesterday = (Calendar) today.clone();
            yesterday.add(Calendar.DAY_OF_YEAR, -1);
            Calendar tomorrow = (Calendar) today.clone();
            tomorrow.add(Calendar.DAY_OF_YEAR, 1);

            if (isSameDay(selectedCalendar, yesterday)) {
                binding.textTodaysPlan.setText(R.string.yesterdays_plan);
            } else if (isSameDay(selectedCalendar, tomorrow)) {
                binding.textTodaysPlan.setText(R.string.tomorrows_plan);
            } else {
                SimpleDateFormat headingFmt = new SimpleDateFormat("MMM d", Locale.getDefault());
                binding.textTodaysPlan.setText(
                        getString(R.string.date_plan_format, headingFmt.format(selectedCalendar.getTime())));
            }
        }

        // --- Date subtitle ---
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault());
        binding.textDate.setText(dateFormat.format(selectedCalendar.getTime()));

        // --- Back to Today button ---
        binding.btnBackToToday.setVisibility(isToday ? View.GONE : View.VISIBLE);
    }

    // ─── Week Calendar (dynamic) ───────────────────────────────────────
    /**
     * Enables week swipe gestures and prepares the horizontal day selector.
     */
    private void setupWeekCalendar() {
        weekSwipeDetector = new GestureDetector(requireContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    private static final int SWIPE_THRESHOLD = 100;
                    private static final int SWIPE_VELOCITY = 100;

                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2,
                                           float velocityX, float velocityY) {
                        if (e1 == null || e2 == null) return false;
                        float dx = e2.getX() - e1.getX();
                        float dy = e2.getY() - e1.getY();
                        if (Math.abs(dx) > Math.abs(dy)
                                && Math.abs(dx) > SWIPE_THRESHOLD
                                && Math.abs(velocityX) > SWIPE_VELOCITY) {
                            boolean swipeLeft = dx < 0;
                            if (swipeLeft) {
                                // Swipe left → next week
                                selectedCalendar.add(Calendar.WEEK_OF_YEAR, 1);
                            } else {
                                // Swipe right → previous week
                                selectedCalendar.add(Calendar.WEEK_OF_YEAR, -1);
                            }
                            updateDateDisplay();
                            animateWeekTransition(swipeLeft);
                            viewModel.setDate(
                                    selectedCalendar.get(Calendar.YEAR),
                                    selectedCalendar.get(Calendar.MONTH),
                                    selectedCalendar.get(Calendar.DAY_OF_MONTH));
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public boolean onDown(MotionEvent e) {
                        return true; // Required for onFling to fire
                    }
                });

        buildWeekView();
    }

    /**
     * Rebuilds the seven-day strip and highlights the selected/today dates.
     */
    private void buildWeekView() {
        LinearLayout container = binding.weekSelector;
        container.removeAllViews();

        Calendar weekStart = (Calendar) selectedCalendar.clone();
        int dayOfWeek = weekStart.get(Calendar.DAY_OF_WEEK);
        int daysFromMonday = (dayOfWeek == Calendar.SUNDAY) ? 6 : dayOfWeek - Calendar.MONDAY;
        weekStart.add(Calendar.DAY_OF_MONTH, -daysFromMonday);

        Calendar today = Calendar.getInstance();
        String[] dayNames = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};

        for (int i = 0; i < 7; i++) {
            Calendar dayCalendar = (Calendar) weekStart.clone();
            dayCalendar.add(Calendar.DAY_OF_MONTH, i);

            boolean isSelected = isSameDay(dayCalendar, selectedCalendar);
            boolean isToday = isSameDay(dayCalendar, today);

            LinearLayout dayColumn = new LinearLayout(requireContext());
            dayColumn.setOrientation(LinearLayout.VERTICAL);
            dayColumn.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            dayColumn.setLayoutParams(params);
            dayColumn.setPadding(8, 12, 8, 12);

            if (isSelected) {
                dayColumn.setBackgroundResource(R.drawable.bg_calendar_selected);
            }

            TextView dayNameTv = new TextView(requireContext());
            dayNameTv.setText(dayNames[i]);
            dayNameTv.setTextSize(12);
            dayNameTv.setGravity(Gravity.CENTER);
            dayNameTv.setTextColor(isSelected
                    ? ContextCompat.getColor(requireContext(), R.color.calendar_selected_text)
                    : ContextCompat.getColor(requireContext(), R.color.text_secondary));
            dayColumn.addView(dayNameTv);

            TextView dayNumTv = new TextView(requireContext());
            dayNumTv.setText(String.valueOf(dayCalendar.get(Calendar.DAY_OF_MONTH)));
            dayNumTv.setTextSize(18);
            dayNumTv.setTypeface(null, Typeface.BOLD);
            dayNumTv.setGravity(Gravity.CENTER);
            dayNumTv.setTextColor(isSelected
                    ? ContextCompat.getColor(requireContext(), R.color.calendar_selected_text)
                    : ContextCompat.getColor(requireContext(), R.color.text_primary));
            dayColumn.addView(dayNumTv);

            if (isToday && !isSelected) {
                View dot = new View(requireContext());
                int dotSize = 6;
                LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dotSize, dotSize);
                dotParams.topMargin = 4;
                dotParams.gravity = Gravity.CENTER;
                dot.setLayoutParams(dotParams);
                dot.setBackgroundResource(R.drawable.bg_timeline_dot_filled);
                dayColumn.addView(dot);
            }

            final Calendar clickedDay = (Calendar) dayCalendar.clone();
            dayColumn.setOnClickListener(v -> {
                selectedCalendar = clickedDay;
                updateDateDisplay();
                buildWeekView();
                viewModel.setDate(
                        clickedDay.get(Calendar.YEAR),
                        clickedDay.get(Calendar.MONTH),
                        clickedDay.get(Calendar.DAY_OF_MONTH)
                );
            });

            dayColumn.setOnTouchListener((v, event) -> {
                weekSwipeDetector.onTouchEvent(event);
                return false; // let clicks still fire
            });

            container.addView(dayColumn);
        }
    }

    /**
     * Animates the week strip when the user swipes between weeks.
     */
    private void animateWeekTransition(boolean swipeLeft) {
        LinearLayout container = binding.weekSelector;
        int width = container.getWidth();
        if (width == 0) { buildWeekView(); return; }

        // Cancel any running animation
        if (weekAnimator != null) weekAnimator.cancel();

        // Phase 1: slide old content out
        float exitTo = swipeLeft ? -width : width;
        weekAnimator = ObjectAnimator.ofFloat(container, "translationX", 0f, exitTo);
        weekAnimator.setDuration(125);
        weekAnimator.setInterpolator(new AccelerateInterpolator());
        weekAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                buildWeekView();
                // Phase 2: slide new content in from opposite side
                float enterFrom = swipeLeft ? width : -width;
                container.setTranslationX(enterFrom);
                weekAnimator = ObjectAnimator.ofFloat(container, "translationX", 0f);
                weekAnimator.setDuration(125);
                weekAnimator.setInterpolator(new DecelerateInterpolator());
                weekAnimator.start();
            }
        });
        weekAnimator.start();
    }

    /**
     * Compares two calendar values by date only.
     */
    private boolean isSameDay(Calendar c1, Calendar c2) {
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR)
                && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR);
    }

    // ─── RecyclerView ──────────────────────────────────────────────────
    /**
     * Configures medication cards and routes take/AI actions to the correct ViewModels.
     */
    private void setupRecyclerView() {
        adapter = new MedicationAdapter(
                item -> viewModel.takeMedication(item),
                item -> {
                    String query = "Tell me about " + item.getName() + ". "
                            + "I take " + item.getDosage()
                            + (item.getInstructions() != null && !item.getInstructions().isEmpty()
                                ? ", " + item.getInstructions() : "")
                            + ". What are its uses, side effects, and any important precautions?";
                    AiAssistantViewModel aiViewModel =
                            new ViewModelProvider(requireActivity()).get(AiAssistantViewModel.class);
                    aiViewModel.setPendingQuery(query);
                    BottomNavigationView bottomNav =
                            requireActivity().findViewById(R.id.bottom_navigation);
                    bottomNav.setSelectedItemId(R.id.aiAssistantFragment);
                }
        );

        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);
    }

    // ─── Observers ─────────────────────────────────────────────────────
    /**
     * Observes medication, loading, error, and summary state from the ViewModel.
     */
    private void setupObservers() {
        viewModel.getMedications().observe(getViewLifecycleOwner(), medications -> {
            if (medications != null) {
                adapter.submitList(medications);
                updateSummaryCard(medications);
                binding.emptyView.setVisibility(medications.isEmpty() ? View.VISIBLE : View.GONE);
                binding.recyclerView.setVisibility(medications.isEmpty() ? View.GONE : View.VISIBLE);
                animateHomeContentChange(medications.isEmpty() ? binding.emptyView : binding.recyclerView);
                pulseView(binding.summaryCard);
            }
        });

        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading ->
                binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE));

        viewModel.getNotificationText().observe(getViewLifecycleOwner(), text -> {
            if (text != null) {
                binding.notificationBar.setText(text);
                if (text.contains("missed")) {
                    binding.notificationBar.setBackgroundResource(R.drawable.bg_notification_bar_warning);
                    binding.notificationBar.setTextColor(ContextCompat.getColor(requireContext(), R.color.error));
                    Drawable icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_warning);
                    if (icon != null) {
                        icon = DrawableCompat.wrap(icon).mutate();
                        DrawableCompat.setTint(icon, ContextCompat.getColor(requireContext(), R.color.error));
                    }
                    binding.notificationBar.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
                } else {
                    binding.notificationBar.setBackgroundResource(R.drawable.bg_notification_bar);
                    binding.notificationBar.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary_dark));
                    Drawable icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_done_all);
                    if (icon != null) {
                        icon = DrawableCompat.wrap(icon).mutate();
                        DrawableCompat.setTint(icon, ContextCompat.getColor(requireContext(), R.color.primary_dark));
                    }
                    binding.notificationBar.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
                }
                pulseView(binding.notificationBar);
            }
        });

        viewModel.getActiveMedications().observe(getViewLifecycleOwner(), activeMeds -> {
            binding.textManageSubtitle.setText(
                    getString(R.string.manage_medications_subtitle_format,
                            activeMeds != null ? activeMeds.size() : 0));
        });

        viewModel.getSelectedDate().observe(getViewLifecycleOwner(), dateStr -> {
            if (dateStr != null) {
                LocalDate selected = LocalDate.parse(dateStr);
                LocalDate today = LocalDate.now();
                adapter.setPastDate(selected.isBefore(today));
                adapter.setFutureDate(selected.isAfter(today));
            }
        });

        viewModel.getError().observe(getViewLifecycleOwner(), errorMsg -> {
            if (errorMsg != null && !errorMsg.isEmpty()) {
                Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Calculates and displays taken, pending, and missed counts for the selected date.
     */
    private void updateSummaryCard(java.util.List<my.utar.uccd3223.medimind.data.local.database.entities.MedicationScheduleItem> medications) {
        int taken = 0;
        int pending = 0;
        int missed = 0;
        for (my.utar.uccd3223.medimind.data.local.database.entities.MedicationScheduleItem item : medications) {
            if (item.isTaken()) {
                taken++;
            } else if (item.isMissed()) {
                missed++;
            } else if (!item.isSkipped()) {
                pending++;
            }
        }
        binding.textSummaryTakenValue.setText(String.valueOf(taken));
        binding.textSummaryPendingValue.setText(String.valueOf(pending));
        binding.textSummaryMissedValue.setText(String.valueOf(missed));
    }

    /**
     * Provides a short fade/slide animation when the home content changes.
     */
    private void animateHomeContentChange(View target) {
        target.setAlpha(0f);
        target.setTranslationY(18f);
        target.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    /**
     * Gives immediate visual feedback when date controls are tapped.
     */
    private void pulseView(View view) {
        view.animate().cancel();
        view.setScaleX(0.98f);
        view.setScaleY(0.98f);
        view.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(180)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    // ─── Buttons ───────────────────────────────────────────────────────
    /**
     * Wires calendar navigation, add medication, settings, theme, and today shortcuts.
     */
    private void setupButtons() {
        // Navigate to Manage Medications screen
        binding.btnAddMedication.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_home_to_manage));

        binding.btnThemeToggle.setOnClickListener(v ->
                ThemeManager.toggleTheme(requireContext()));

        binding.btnCalendar.setOnClickListener(v -> {
            DatePickerDialog picker = new DatePickerDialog(
                    requireContext(),
                    (view, year, month, dayOfMonth) -> {
                        selectedCalendar.set(year, month, dayOfMonth);
                        updateDateDisplay();
                        buildWeekView();
                        viewModel.setDate(year, month, dayOfMonth);
                    },
                    selectedCalendar.get(Calendar.YEAR),
                    selectedCalendar.get(Calendar.MONTH),
                    selectedCalendar.get(Calendar.DAY_OF_MONTH)
            );
            picker.show();
        });

        binding.btnSettings.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.action_home_to_settings));

        binding.btnBackToToday.setOnClickListener(v -> {
            selectedCalendar = Calendar.getInstance();
            updateDateDisplay();
            buildWeekView();
            viewModel.setDate(
                    selectedCalendar.get(Calendar.YEAR),
                    selectedCalendar.get(Calendar.MONTH),
                    selectedCalendar.get(Calendar.DAY_OF_MONTH));
        });
    }

    /**
     * Syncs the theme toggle icon with the currently stored theme.
     */
    private void updateThemeToggleState() {
        boolean darkMode = ThemeManager.isDarkMode(requireContext());
        binding.btnThemeToggle.setImageResource(
                darkMode ? R.drawable.ic_dark_mode : R.drawable.ic_light_mode
        );
        binding.btnThemeToggle.setContentDescription(
                getString(darkMode ? R.string.switch_to_light : R.string.switch_to_dark)
        );
    }

    /**
     * Refreshes dates and medications when returning to the home tab.
     */
    @Override
    public void onResume() {
        super.onResume();
        updateThemeToggleState();
        // Recalculate date flags in case midnight has passed since last load
        String dateStr = viewModel.getSelectedDate().getValue();
        if (dateStr != null) {
            LocalDate selected = LocalDate.parse(dateStr);
            LocalDate today = LocalDate.now();
            adapter.setPastDate(selected.isBefore(today));
            adapter.setFutureDate(selected.isAfter(today));
        }
        // Reload medications when returning from manage screen
        viewModel.loadMedications();
    }

    /**
     * Clears binding references when the home view is destroyed.
     */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
