package my.utar.uccd3223.medimind.presentation.home;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import my.utar.uccd3223.medimind.R;
import my.utar.uccd3223.medimind.data.local.database.entities.MedicationScheduleItem;
import my.utar.uccd3223.medimind.databinding.ItemMedicationBinding;
import my.utar.uccd3223.medimind.util.MedicationImageUtils;

/**
 * Binds home medication schedule cards and exposes take/AI-info actions.
 */
public class MedicationAdapter extends ListAdapter<MedicationScheduleItem, MedicationAdapter.ViewHolder> {

    private final OnTakeClickListener takeListener;
    private final OnAiClickListener aiListener;
    private boolean isPastDate = false;
    private boolean isFutureDate = false;

    public void setPastDate(boolean pastDate) {
        if (this.isPastDate != pastDate) {
            this.isPastDate = pastDate;
            notifyDataSetChanged();
        }
    }

    public void setFutureDate(boolean futureDate) {
        if (this.isFutureDate != futureDate) {
            this.isFutureDate = futureDate;
            notifyDataSetChanged();
        }
    }

    public interface OnTakeClickListener {
        void onTakeClick(MedicationScheduleItem item);
    }

    public interface OnAiClickListener {
        void onAiClick(MedicationScheduleItem item);
    }

    public MedicationAdapter(OnTakeClickListener takeListener, OnAiClickListener aiListener) {
        super(DIFF_CALLBACK);
        this.takeListener = takeListener;
        this.aiListener = aiListener;
    }

    private static final DiffUtil.ItemCallback<MedicationScheduleItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<MedicationScheduleItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull MedicationScheduleItem oldItem,
                                               @NonNull MedicationScheduleItem newItem) {
                    return oldItem.getId() == newItem.getId()
                            && oldItem.getScheduleId() == newItem.getScheduleId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull MedicationScheduleItem oldItem,
                                                  @NonNull MedicationScheduleItem newItem) {
                    return oldItem.getName().equals(newItem.getName())
                            && oldItem.getDosage().equals(newItem.getDosage())
                            && String.valueOf(oldItem.getTakenDateTime())
                                .equals(String.valueOf(newItem.getTakenDateTime()))
                            && String.valueOf(oldItem.getTodayStatus())
                                .equals(String.valueOf(newItem.getTodayStatus()));
                }
            };

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMedicationBinding binding = ItemMedicationBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), position == getItemCount() - 1);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemMedicationBinding binding;

        ViewHolder(ItemMedicationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(MedicationScheduleItem item, boolean isLastItem) {
            // Time label
            binding.textTime.setText(item.getFormattedTime());

            // Name
            binding.textName.setText(item.getName());

            // Dosage + instructions
            String dosageText = item.getDosage();
            if (item.getInstructions() != null && !item.getInstructions().isEmpty()) {
                dosageText += "  " + item.getInstructions();
            }
            binding.textDosage.setText(dosageText);

            if (item.isTaken()) {
                String takenDisplay = formatTakenTime(item.getTakenDateTime());
                if (takenDisplay != null) {
                    binding.textTakenTime.setVisibility(View.VISIBLE);
                    binding.textTakenTime.setText("Taken at " + takenDisplay);
                } else {
                    binding.textTakenTime.setVisibility(View.GONE);
                    binding.textTakenTime.setText(null);
                }
            } else {
                binding.textTakenTime.setVisibility(View.GONE);
                binding.textTakenTime.setText(null);
            }

            // Medication image
            float density = itemView.getContext().getResources().getDisplayMetrics().density;
            MedicationImageUtils.loadMedicationImage(binding.imgMedication, item.getImageUrl());

            // Timeline dot, card background, button state, and warning compound drawable
            boolean isTaken = item.isTaken();
            boolean isMissed = item.isMissed();
            boolean isSkipped = item.isSkipped();
            boolean isUpcoming = item.isUpcoming();

            // Defensive: past-date items with non-final status must display as missed
            if (isPastDate && !isTaken && !isMissed && !isSkipped) {
                isMissed = true;
            }
            // Defensive: future-date items with non-final status must display as upcoming
            if (isFutureDate && !isTaken && !isSkipped && !isMissed) {
                isUpcoming = true;
            }

            if (isTaken) {
                binding.timelineDot.setBackgroundResource(R.drawable.bg_timeline_dot_filled);
                binding.medicationCard.setCardBackgroundColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.success_surface));
                applyStatusBadge(binding.textStatusBadge, R.string.status_badge_taken,
                        R.drawable.bg_status_pill_taken, R.color.success);
                binding.btnTakeNow.setCompoundDrawables(null, null, null, null);
                binding.btnTakeNow.setText("Taken");
                binding.btnTakeNow.setEnabled(false);
                binding.btnTakeNow.setAlpha(0.7f);
                binding.btnTakeNow.setBackgroundResource(R.drawable.bg_button_taken_disabled);
                binding.btnTakeNow.setTextColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.text_secondary));
            } else if (isMissed) {
                binding.timelineDot.setBackgroundResource(R.drawable.bg_timeline_dot_missed);
                binding.medicationCard.setCardBackgroundColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.error_surface));
                applyStatusBadge(binding.textStatusBadge, R.string.status_badge_missed,
                        R.drawable.bg_status_pill_missed, R.color.error);

                // Warning icon as compound drawable on the button
                Drawable warn = ContextCompat.getDrawable(itemView.getContext(), R.drawable.ic_warning);
                if (warn != null) {
                    warn = DrawableCompat.wrap(warn).mutate();
                    DrawableCompat.setTint(warn, ContextCompat.getColor(itemView.getContext(), R.color.error));
                    int sz = (int) (18 * density);
                    warn.setBounds(0, 0, sz, sz);
                }
                binding.btnTakeNow.setCompoundDrawables(warn, null, null, null);
                binding.btnTakeNow.setCompoundDrawablePadding((int) (6 * density));

                if (isPastDate) {
                    // Past day: permanently missed, cannot take
                    binding.btnTakeNow.setText("Missed");
                    binding.btnTakeNow.setEnabled(false);
                    binding.btnTakeNow.setAlpha(1.0f);
                    binding.btnTakeNow.setBackgroundResource(R.drawable.bg_button_taken_disabled);
                    binding.btnTakeNow.setTextColor(
                            ContextCompat.getColor(itemView.getContext(), R.color.text_secondary));
                } else {
                    // Today: allow catch-up
                    binding.btnTakeNow.setText(R.string.take_now);
                    binding.btnTakeNow.setEnabled(true);
                    binding.btnTakeNow.setAlpha(1.0f);
                    binding.btnTakeNow.setBackgroundResource(R.drawable.bg_button_take_now_missed);
                    binding.btnTakeNow.setTextColor(
                            ContextCompat.getColor(itemView.getContext(), R.color.on_primary));
                }
            } else if (isSkipped) {
                binding.timelineDot.setBackgroundResource(R.drawable.bg_timeline_dot_filled);
                binding.medicationCard.setCardBackgroundColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.surface_elevated));
                applyStatusBadge(binding.textStatusBadge, R.string.status_badge_skipped,
                        R.drawable.bg_status_pill_upcoming, R.color.primary_dark);
                binding.btnTakeNow.setCompoundDrawables(null, null, null, null);
                binding.btnTakeNow.setText(R.string.skipped);
                binding.btnTakeNow.setEnabled(false);
                binding.btnTakeNow.setAlpha(0.7f);
                binding.btnTakeNow.setBackgroundResource(R.drawable.bg_button_taken_disabled);
                binding.btnTakeNow.setTextColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.text_secondary));
            } else if (isUpcoming) {
                // Scheduled time hasn't arrived yet (today or future date)
                binding.timelineDot.setBackgroundResource(R.drawable.bg_timeline_dot_empty);
                binding.medicationCard.setCardBackgroundColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.info_surface));
                applyStatusBadge(binding.textStatusBadge, R.string.status_badge_upcoming,
                        R.drawable.bg_status_pill_upcoming, R.color.primary_dark);
                binding.btnTakeNow.setCompoundDrawables(null, null, null, null);
                binding.btnTakeNow.setText(R.string.upcoming);
                binding.btnTakeNow.setEnabled(false);
                binding.btnTakeNow.setAlpha(1.0f);
                binding.btnTakeNow.setBackgroundResource(R.drawable.bg_button_upcoming);
                binding.btnTakeNow.setTextColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.on_primary));
            } else {
                // PENDING fallback - show Take Now
                binding.timelineDot.setBackgroundResource(R.drawable.bg_timeline_dot_empty);
                binding.medicationCard.setCardBackgroundColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.warning_surface));
                applyStatusBadge(binding.textStatusBadge, R.string.status_badge_pending,
                        R.drawable.bg_status_pill_pending, R.color.warning);
                binding.btnTakeNow.setCompoundDrawables(null, null, null, null);
                binding.btnTakeNow.setText(R.string.take_now);
                binding.btnTakeNow.setEnabled(true);
                binding.btnTakeNow.setAlpha(1.0f);
                binding.btnTakeNow.setBackgroundResource(R.drawable.bg_take_now_button);
                binding.btnTakeNow.setTextColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.on_primary));
            }

            // Hide timeline line for last item
            binding.timelineLine.setVisibility(isLastItem ? View.INVISIBLE : View.VISIBLE);

            binding.btnTakeNow.setOnClickListener(v -> {
                if (takeListener != null && !item.isTaken() && !item.isSkipped()) {
                    takeListener.onTakeClick(item);
                }
            });

            binding.btnAiInfo.setOnClickListener(v -> {
                if (aiListener != null) {
                    aiListener.onAiClick(item);
                }
            });
        }

        private String formatTakenTime(String takenDateTime) {
            if (takenDateTime == null || takenDateTime.isEmpty()) return null;
            try {
                String timePart = takenDateTime.contains(" ")
                        ? takenDateTime.split(" ")[1] : takenDateTime;
                String[] parts = timePart.split(":");
                int hour = Integer.parseInt(parts[0]);
                int minute = Integer.parseInt(parts[1]);
                String amPm = hour >= 12 ? "PM" : "AM";
                int displayHour = hour % 12;
                if (displayHour == 0) displayHour = 12;
                return String.format("%d:%02d %s", displayHour, minute, amPm);
            } catch (Exception e) {
                return null;
            }
        }

        private void applyStatusBadge(TextView badgeView, int textRes, int backgroundRes, int textColorRes) {
            badgeView.setText(textRes);
            badgeView.setBackgroundResource(backgroundRes);
            badgeView.setTextColor(ContextCompat.getColor(itemView.getContext(), textColorRes));
        }
    }
}
