package my.utar.uccd3223.medimind.presentation.manage;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import java.time.LocalDate;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import my.utar.uccd3223.medimind.R;
import my.utar.uccd3223.medimind.data.local.database.entities.Medication;
import my.utar.uccd3223.medimind.databinding.ItemManageMedicationBinding;
import my.utar.uccd3223.medimind.util.MedicationImageUtils;

/**
 * Binds medication rows in the manage screen and exposes edit/delete callbacks.
 */
public class ManageMedicationAdapter extends ListAdapter<Medication, ManageMedicationAdapter.ViewHolder> {

    private final OnEditClickListener editListener;
    private final OnDeleteClickListener deleteListener;

    public interface OnEditClickListener {
        void onEditClick(Medication medication);
    }

    public interface OnDeleteClickListener {
        void onDeleteClick(Medication medication);
    }

    public ManageMedicationAdapter(OnEditClickListener editListener, OnDeleteClickListener deleteListener) {
        super(DIFF_CALLBACK);
        this.editListener = editListener;
        this.deleteListener = deleteListener;
    }

    private static final DiffUtil.ItemCallback<Medication> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Medication>() {
                @Override
                public boolean areItemsTheSame(@NonNull Medication oldItem, @NonNull Medication newItem) {
                    return oldItem.getId() == newItem.getId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull Medication oldItem, @NonNull Medication newItem) {
                    return oldItem.getName().equals(newItem.getName())
                            && oldItem.getDosage().equals(newItem.getDosage())
                            && String.valueOf(oldItem.getFrequency()).equals(String.valueOf(newItem.getFrequency()))
                            && oldItem.getUpdatedAt() == newItem.getUpdatedAt();
                }
            };

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemManageMedicationBinding binding = ItemManageMedicationBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemManageMedicationBinding binding;

        ViewHolder(ItemManageMedicationBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Medication medication) {
            binding.textName.setText(medication.getName());
            binding.textDosage.setText(medication.getDosage());

            // Frequency label
            String freqLabel = getFrequencyLabel(medication.getFrequency());
            binding.textFrequency.setText(freqLabel);

            // Date range
            String dateRange = medication.getStartDate();
            if (medication.getEndDate() != null && !medication.getEndDate().isEmpty()) {
                dateRange += " to " + medication.getEndDate();
            } else {
                dateRange += " - Ongoing";
            }
            binding.textDateRange.setText(dateRange);

            // Medication image
            MedicationImageUtils.loadMedicationImage(binding.imgMedication, medication.getImageUrl());

            // Expired card styling
            boolean isExpired = false;
            String endDate = medication.getEndDate();
            if (endDate != null && !endDate.isEmpty()) {
                try {
                    isExpired = LocalDate.parse(endDate).isBefore(LocalDate.now());
                } catch (Exception ignored) {}
            }

            if (isExpired) {
                binding.getRoot().setCardBackgroundColor(
                        itemView.getContext().getResources().getColor(R.color.expired_card_bg, null));
                binding.textName.setTextColor(
                        itemView.getContext().getResources().getColor(R.color.text_secondary, null));
                binding.imgMedication.setAlpha(0.5f);
            } else {
                binding.getRoot().setCardBackgroundColor(
                        itemView.getContext().getResources().getColor(R.color.surface, null));
                binding.textName.setTextColor(
                        itemView.getContext().getResources().getColor(R.color.text_primary, null));
                binding.imgMedication.setAlpha(1.0f);
            }

            binding.btnEdit.setOnClickListener(v -> {
                if (editListener != null) editListener.onEditClick(medication);
            });

            binding.btnDelete.setOnClickListener(v -> {
                if (deleteListener != null) deleteListener.onDeleteClick(medication);
            });
        }

        private String getFrequencyLabel(String frequency) {
            if (frequency == null || !frequency.contains("/")) {
                return frequency != null ? frequency : "Daily";
            }
            try {
                String[] parts = frequency.split("/");
                int doses = Integer.parseInt(parts[0].trim());
                int days = Integer.parseInt(parts[1].trim());

                if (doses == 1 && days == 1) return "Daily";
                if (doses == 2 && days == 1) return "Twice Daily";
                if (doses == 3 && days == 1) return "3 Times Daily";
                if (doses == 1 && days == 2) return "Every 2 Days";
                if (doses == 1 && days == 3) return "Every 3 Days";
                if (doses == 1 && days == 7) return "Weekly";
                return doses + " time(s) per " + days + " day(s)";
            } catch (Exception e) {
                return frequency;
            }
        }
    }
}
