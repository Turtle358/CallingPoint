package com.example.callingpoint;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class CoachAdapter extends RecyclerView.Adapter<CoachAdapter.ViewHolder> {
    private final List<ServiceDetailsResponse.Coach> coaches;

    public CoachAdapter(List<ServiceDetailsResponse.Coach> coaches) {
        this.coaches = coaches;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_coach, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ServiceDetailsResponse.Coach coach = coaches.get(position);

        if (coach.number != null && !coach.number.trim().isEmpty()) {
            holder.tvCoachNumber.setText(coach.number);
        } else {
            holder.tvCoachNumber.setText(String.valueOf(position + 1));
        }

        if (coach.toilet != null && coach.toilet.value != null) {
            String toiletType = coach.toilet.value;

            if (!toiletType.equalsIgnoreCase("None") && !toiletType.trim().isEmpty()) {
                holder.iconFacility.setVisibility(View.VISIBLE);

                if (toiletType.toLowerCase().contains("accessible")) {
                    holder.iconFacility.setImageResource(R.drawable.ic_wheelchair);
                } else {
                    holder.iconFacility.setImageResource(R.drawable.ic_toilet);
                }
            } else {
                holder.iconFacility.setVisibility(View.GONE);
            }
        } else {
            holder.iconFacility.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() { return coaches.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvCoachNumber;
        ImageView iconFacility;
        public ViewHolder(View v) {
            super(v);
            tvCoachNumber = v.findViewById(R.id.tvCoachNumber);
            iconFacility = v.findViewById(R.id.iconFacility);
        }
    }
}