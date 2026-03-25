package my.utar.uccd3223.medimind.presentation.home;

import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import my.utar.uccd3223.medimind.R;
import my.utar.uccd3223.medimind.data.local.database.entities.MedicationScheduleItem;
import my.utar.uccd3223.medimind.databinding.ItemMedicationBinding;

public class MedicationAdapter extends ListAdapter<MedicationScheduleItem, MedicationAdapter.ViewHolder> {

    private final OnTakeClickListener takeListener;
    private final OnAiClickListener aiListener;
    private boolean isPastDate = false;
    private boolean isFutureDate = false;

    public void setPastDate(boolean pastDate) {
        this.isPastDate = pastDate;
    }

    public void setFutureDate(boolean futureDate) {
        this.isFutureDate = futureDate;
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

            // Medication image
            float density = itemView.getContext().getResources().getDisplayMetrics().density;
            if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
                try {
                    binding.imgMedication.setPadding(0, 0, 0, 0);
                    binding.imgMedication.setImageURI(Uri.parse(item.getImageUrl()));
                } catch (Exception e) {
                    int pad = (int) (8 * density);
                    binding.imgMedication.setPadding(pad, pad, pad, pad);
                    binding.imgMedication.setImageResource(R.drawable.ic_default_medication);
                }
            } else {
                int pad = (int) (8 * density);
                binding.imgMedication.setPadding(pad, pad, pad, pad);
                binding.imgMedication.setImageResource(R.drawable.ic_default_medication);
            }

            // Timeline dot, card background, button state, and warning compound drawable
            boolean isTaken = item.isTaken();
            boolean isMissed = item.isMissed();
            boolean isSkipped = item.isSkipped();
            boolean isUpcoming = item.isUpcoming();

            if (isTaken) {
                binding.timelineDot.setBackgroundResource(R.drawable.bg_timeline_dot_filled);
                binding.medicationCard.setCardBackgroundColor(
                        ContextCompat.getColor(itemView.getContext(), R.color.surface));
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
                        ContextCompat.getColor(itemView.getContext(), R.color.error_light));

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
                        ContextCompat.getColor(itemView.getContext(), R.color.surface));
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
                        ContextCompat.getColor(itemView.getContext(), R.color.surface));
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
                        ContextCompat.getColor(itemView.getContext(), R.color.surface));
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
    }
}
