package io.github.turtle358.callingpoint.ui.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.models.JourneyOption;
import io.github.turtle358.callingpoint.models.Toc;
import io.github.turtle358.callingpoint.ui.activities.JourneySearchActivity;
import io.github.turtle358.callingpoint.ui.adapters.PinnedJourneyAdapter;
import io.github.turtle358.callingpoint.utils.PinnedJourneyManager;
import io.github.turtle358.callingpoint.utils.TOCBrandHelper;

public class JourneyPlannerFragment extends Fragment {

    private RecyclerView recyclerPinnedJourneys;
    private View layoutEmptyState;
    private TextView tvListHeader;

    private PinnedJourneyAdapter adapter;
    private PinnedJourneyManager manager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_journey_planner, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerPinnedJourneys = view.findViewById(R.id.recyclerPinnedJourneys);
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState);
        tvListHeader = view.findViewById(R.id.tvListHeader);
        CardView cardMockSearch = view.findViewById(R.id.cardMockSearch);

        recyclerPinnedJourneys.setLayoutManager(new LinearLayoutManager(requireContext()));

        manager = new PinnedJourneyManager(requireContext());
        setupDragAndDrop();

        cardMockSearch.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), JourneySearchActivity.class));
        });

        setupDelayRepayHub(view);
    }

    private void setupDragAndDrop() {
        androidx.recyclerview.widget.ItemTouchHelper touchHelper = new androidx.recyclerview.widget.ItemTouchHelper(
                new androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
                        androidx.recyclerview.widget.ItemTouchHelper.UP | androidx.recyclerview.widget.ItemTouchHelper.DOWN,
                        androidx.recyclerview.widget.ItemTouchHelper.LEFT | androidx.recyclerview.widget.ItemTouchHelper.RIGHT) {

                    @Override
                    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                        if (adapter == null) return false;

                        int fromPos = viewHolder.getBindingAdapterPosition();
                        int toPos = target.getBindingAdapterPosition();

                        if (fromPos != RecyclerView.NO_POSITION && toPos != RecyclerView.NO_POSITION) {
                            adapter.swapItems(fromPos, toPos);
                        }
                        return true;
                    }

                    @Override
                    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                        if (adapter == null || manager == null) return;

                        viewHolder.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);

                        int position = viewHolder.getBindingAdapterPosition();
                        if (position == RecyclerView.NO_POSITION) return;

                        JourneyOption unpinnedJourney = adapter.getItems().get(position);
                        adapter.getItems().remove(position);
                        adapter.notifyItemRemoved(position);

                        manager.saveAllPinnedJourneys(adapter.getItems());
                        updateEmptyState();

                        com.google.android.material.snackbar.Snackbar.make(requireView(), "Journey unpinned", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                                .setAction("Undo", v -> {
                                    v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                                    adapter.restoreItem(unpinnedJourney, position);
                                    recyclerPinnedJourneys.scrollToPosition(position);
                                    manager.saveAllPinnedJourneys(adapter.getItems());
                                    updateEmptyState();
                                }).show();
                    }

                    @Override
                    public void onSelectedChanged(@androidx.annotation.Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
                        super.onSelectedChanged(viewHolder, actionState);
                        if (viewHolder != null && (actionState == androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_DRAG || actionState == androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_SWIPE)) {
                            viewHolder.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                        }
                    }

                    @Override
                    public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                        super.clearView(recyclerView, viewHolder);
                        if (adapter != null && manager != null) {
                            manager.saveAllPinnedJourneys(adapter.getItems());
                        }
                    }

                    @Override
                    public void onChildDraw(@NonNull android.graphics.Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                        if (actionState == androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_SWIPE) {
                            drawSwipeBackground(c, viewHolder.itemView, dX);
                        }
                        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                    }
                });

        touchHelper.attachToRecyclerView(recyclerPinnedJourneys);
    }

    private void drawSwipeBackground(android.graphics.Canvas c, View itemView, float dX) {
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setColor(android.graphics.Color.parseColor("#FFB4AB"));
        paint.setAntiAlias(true);

        float cornerRadius = itemView.getResources().getDisplayMetrics().density * 30;

        android.graphics.RectF background = new android.graphics.RectF(
                itemView.getLeft(),
                itemView.getTop(),
                itemView.getRight(),
                itemView.getBottom()
        );

        c.drawRoundRect(background, cornerRadius, cornerRadius, paint);

        android.graphics.drawable.Drawable icon = androidx.core.content.ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_delete);
        if (icon != null) {
            icon.setTint(android.graphics.Color.parseColor("#690005"));
            int iconMargin = (itemView.getHeight() - icon.getIntrinsicHeight()) / 2;
            int iconTop = itemView.getTop() + (itemView.getHeight() - icon.getIntrinsicHeight()) / 2;
            int iconBottom = iconTop + icon.getIntrinsicHeight();

            if (dX > 0) {
                int iconLeft = itemView.getLeft() + iconMargin;
                int iconRight = iconLeft + icon.getIntrinsicWidth();
                icon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                icon.draw(c);
            } else if (dX < 0) {
                int iconRight = itemView.getRight() - iconMargin;
                int iconLeft = iconRight - icon.getIntrinsicWidth();
                icon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                icon.draw(c);
            }
        }
    }

    private void setupDelayRepayHub(View view) {
        LinearLayout header = view.findViewById(R.id.delayRepayHeader);
        ImageView iconExpand = view.findViewById(R.id.iconExpandDelay);
        RecyclerView recyclerDelayRepay = view.findViewById(R.id.recyclerDelayRepayTocs);
        CardView cardDelayRepayHub = view.findViewById(R.id.cardDelayRepayHub);

        List<Toc> visibleTocs = TOCBrandHelper.getVisibleTocs();
        List<Toc> validTocs = new ArrayList<>();

        for (Toc toc : visibleTocs) {
            if (toc.delayRepayUrl != null && !toc.delayRepayUrl.isEmpty()) {
                validTocs.add(toc);
            }
        }

        recyclerDelayRepay.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerDelayRepay.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_disruption, parent, false);
                return new RecyclerView.ViewHolder(v) {};
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                Toc toc = validTocs.get(position);
                TextView tvName = holder.itemView.findViewById(R.id.tvTocName);
                TextView tvCode = holder.itemView.findViewById(R.id.tvTocCode);
                TextView tvStatus = holder.itemView.findViewById(R.id.tvStatus);

                tvName.setText(toc.name);
                tvCode.setText(toc.acronym != null ? toc.acronym : toc.code);
                System.out.println(toc.name+ " | " + toc.delayRepayMinTime + " | " + toc.delayRepayMessage);

                if(!Objects.equals(toc.delayRepayMessage, "")) {
                    tvStatus.setText(toc.delayRepayMessage);
                    tvStatus.setVisibility(View.VISIBLE);
                } else {
                    tvStatus.setVisibility(View.GONE);
                }

                if (holder.itemView instanceof ViewGroup && ((ViewGroup) holder.itemView).getChildCount() > 2) {
                    ((ViewGroup) holder.itemView).getChildAt(2).setVisibility(View.GONE);
                }

                int brandColour = TOCBrandHelper.getColourForToc(toc.name);
                tvCode.setBackgroundTintList(android.content.res.ColorStateList.valueOf(brandColour));

                boolean isLightColour = androidx.core.graphics.ColorUtils.calculateLuminance(brandColour) > 0.5;
                tvCode.setTextColor(isLightColour ? android.graphics.Color.BLACK : android.graphics.Color.WHITE);

                holder.itemView.setOnClickListener(v -> {
                    CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
                    customTabsIntent.launchUrl(requireContext(), Uri.parse(toc.delayRepayUrl));
                });
            }

            @Override
            public int getItemCount() {
                return validTocs.size();
            }
        });

        header.setOnClickListener(v -> {
            boolean isExpanded = recyclerDelayRepay.getVisibility() == View.VISIBLE;
            android.transition.TransitionManager.beginDelayedTransition((ViewGroup) cardDelayRepayHub.getParent());
            recyclerDelayRepay.setVisibility(isExpanded ? View.GONE : View.VISIBLE);
            iconExpand.animate().rotation(isExpanded ? 0f : 180f).setDuration(200).start();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadPinnedJourneys();
    }

    private void loadPinnedJourneys() {
        if (manager == null) return;

        List<JourneyOption> savedJourneys = manager.getPinnedJourneys();

        if (savedJourneys != null) {
            adapter = new PinnedJourneyAdapter(requireContext(), savedJourneys);
            recyclerPinnedJourneys.setAdapter(adapter);
        }

        updateEmptyState();
    }

    private void updateEmptyState() {
        if (adapter != null && adapter.getItemCount() > 0) {
            tvListHeader.setVisibility(View.VISIBLE);
            layoutEmptyState.setVisibility(View.GONE);
            recyclerPinnedJourneys.setVisibility(View.VISIBLE);
        } else {
            tvListHeader.setVisibility(View.GONE);
            recyclerPinnedJourneys.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);
        }
    }
}