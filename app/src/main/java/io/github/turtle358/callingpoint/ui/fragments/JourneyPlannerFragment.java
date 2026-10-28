package io.github.turtle358.callingpoint.ui.fragments;

import android.content.Intent;
import com.google.gson.Gson;
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
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.turtle358.callingpoint.R;
import io.github.turtle358.callingpoint.models.JourneyOption;
import io.github.turtle358.callingpoint.models.SavedRoute;
import io.github.turtle358.callingpoint.models.Toc;
import io.github.turtle358.callingpoint.ui.activities.JourneySearchActivity;
import io.github.turtle358.callingpoint.ui.adapters.PinnedJourneyAdapter;
import io.github.turtle358.callingpoint.ui.adapters.SavedRoutesAdapter;
import io.github.turtle358.callingpoint.utils.JourneyPlanner.PinnedJourneyManager;
import io.github.turtle358.callingpoint.utils.JourneyPlanner.SavedRoutesManager;
import io.github.turtle358.callingpoint.utils.TOCBrandHelper;

public class JourneyPlannerFragment extends Fragment {

    private RecyclerView recyclerPinnedJourneys;
    private View layoutEmptyState;
    private TextView tvListHeader;

    private PinnedJourneyAdapter adapter;
    private PinnedJourneyManager pinnedJourneyManager;
    private RecyclerView recyclerSavedRoutes;
    private View tvSavedRoutesHeader;
    private SavedRoutesManager savedRoutesManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_journey_planner, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerPinnedJourneys = view.findViewById(R.id.recyclerPinnedJourneys);
        recyclerSavedRoutes = view.findViewById(R.id.recyclerSavedRoutes);
        tvSavedRoutesHeader = view.findViewById(R.id.tvSavedRoutesHeader);
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState);
        tvListHeader = view.findViewById(R.id.tvListHeader);
        CardView cardMockSearch = view.findViewById(R.id.cardMockSearch);

        recyclerPinnedJourneys.setLayoutManager(new LinearLayoutManager(requireContext()));

        if (recyclerSavedRoutes != null) {
            recyclerSavedRoutes.setLayoutManager(new LinearLayoutManager(requireContext()));
            recyclerSavedRoutes.setNestedScrollingEnabled(false);
        }

        pinnedJourneyManager = new PinnedJourneyManager(requireContext());
        savedRoutesManager = new SavedRoutesManager(requireContext());
        setupDragAndDrop();
        setupSavedRouteSwipe();

        cardMockSearch.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), JourneySearchActivity.class));
        });

        setupDelayRepayHub(view);
    }

    private void setupSavedRouteSwipe() {
        if (recyclerSavedRoutes == null) return;

        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                if (!(recyclerSavedRoutes.getAdapter() instanceof SavedRoutesAdapter)) return;
                SavedRoutesAdapter savedAdapter = (SavedRoutesAdapter) recyclerSavedRoutes.getAdapter();
                int position = viewHolder.getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION || position >= savedAdapter.getItemCount()) {
                    loadSavedRoutes();
                    return;
                }
                SavedRoute route = savedAdapter.getRoutes().get(position);
                deleteSavedRoute(route, position);
            }

            @Override
            public void onChildDraw(@NonNull android.graphics.Canvas c,
                                    @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY, int actionState, boolean isCurrentlyActive) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX != 0f) {
                    drawSavedRouteSwipeBackground(c, viewHolder.itemView, dX);
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        };
        new ItemTouchHelper(callback).attachToRecyclerView(recyclerSavedRoutes);
    }

    private void drawSavedRouteSwipeBackground(@NonNull android.graphics.Canvas canvas,
                                               @NonNull View item,
                                               float dX) {
        float density = getResources().getDisplayMetrics().density;
        float radius = 22f * density;
        float bottomMargin = 8f * density;

        android.graphics.RectF area = new android.graphics.RectF(
                item.getLeft(), item.getTop(),
                item.getRight(), item.getBottom() - bottomMargin
        );
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setColor(android.graphics.Color.parseColor("#FFB4AB"));
        canvas.drawRoundRect(area, radius, radius, paint);

        android.graphics.drawable.Drawable bin = androidx.core.content.ContextCompat.getDrawable(
                requireContext(), android.R.drawable.ic_menu_delete);
        if (bin == null) return;

        int iconSize = Math.round(24f * density);
        int iconInset = Math.round(28f * density);
        int top = Math.round(area.centerY() - iconSize / 2f);
        int left = dX > 0f
                ? Math.round(area.left) + iconInset
                : Math.round(area.right) - iconInset - iconSize;

        if (Math.abs(dX) < iconInset + iconSize) return;

        bin = bin.mutate();
        bin.setTint(android.graphics.Color.parseColor("#690005"));
        bin.setBounds(left, top, left + iconSize, top + iconSize);
        bin.draw(canvas);
    }

    private void deleteSavedRoute(@NonNull SavedRoute route, int previousPosition) {
        savedRoutesManager.removeRoute(route);
        loadSavedRoutes();
        updateEmptyState();

        com.google.android.material.snackbar.Snackbar.make(requireView(),
                "Saved route removed", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                .setAction("Undo", view -> {
                    List<SavedRoute> current = new ArrayList<>(savedRoutesManager.getSavedRoutes());
                    boolean exists = false;
                    for (SavedRoute candidate : current) {
                        if (candidate.routeKey().equals(route.routeKey())) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        current.add(Math.min(Math.max(previousPosition, 0), current.size()), route);
                        savedRoutesManager.saveAll(current);
                    }
                    loadSavedRoutes();
                    updateEmptyState();
                })
                .show();
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
                        if (adapter == null || pinnedJourneyManager == null) return;

                        viewHolder.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);

                        int position = viewHolder.getBindingAdapterPosition();
                        if (position == RecyclerView.NO_POSITION) return;

                        JourneyOption unpinnedJourney = adapter.getItems().get(position);
                        adapter.getItems().remove(position);
                        adapter.notifyItemRemoved(position);

                        pinnedJourneyManager.saveAllPinnedJourneys(adapter.getItems());
                        updateEmptyState();

                        com.google.android.material.snackbar.Snackbar.make(requireView(), "Journey unpinned", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                                .setAction("Undo", v -> {
                                    v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                                    adapter.restoreItem(unpinnedJourney, position);
                                    recyclerPinnedJourneys.scrollToPosition(position);
                                    pinnedJourneyManager.saveAllPinnedJourneys(adapter.getItems());
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
                    public void onChildDraw(@NonNull android.graphics.Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                        if (actionState == androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_SWIPE) {
                            drawSwipeBackground(c, viewHolder.itemView, dX);

                            androidx.cardview.widget.CardView notchLeft = viewHolder.itemView.findViewById(R.id.notchLeft);
                            androidx.cardview.widget.CardView notchRight = viewHolder.itemView.findViewById(R.id.notchRight);
                            View notchLeftMask = viewHolder.itemView.findViewById(R.id.notchLeftMask);
                            View notchRightMask = viewHolder.itemView.findViewById(R.id.notchRightMask);

                            if (notchLeft != null && notchRight != null && notchLeftMask != null && notchRightMask != null) {
                                int swipeColor = android.graphics.Color.parseColor("#FFB4AB");
                                int defaultColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.bg_screen);

                                if (dX > 0) {
                                    notchLeft.setCardBackgroundColor(swipeColor);
                                    notchLeftMask.setBackgroundColor(swipeColor);
                                    notchRight.setCardBackgroundColor(defaultColor);
                                    notchRightMask.setBackgroundColor(defaultColor);
                                } else if (dX < 0) {
                                    notchLeft.setCardBackgroundColor(defaultColor);
                                    notchLeftMask.setBackgroundColor(defaultColor);
                                    notchRight.setCardBackgroundColor(swipeColor);
                                    notchRightMask.setBackgroundColor(swipeColor);
                                } else {
                                    notchLeft.setCardBackgroundColor(defaultColor);
                                    notchLeftMask.setBackgroundColor(defaultColor);
                                    notchRight.setCardBackgroundColor(defaultColor);
                                    notchRightMask.setBackgroundColor(defaultColor);
                                }
                            }
                        }
                        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
                    }

                    @Override
                    public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                        super.clearView(recyclerView, viewHolder);

                        androidx.cardview.widget.CardView notchLeft = viewHolder.itemView.findViewById(R.id.notchLeft);
                        androidx.cardview.widget.CardView notchRight = viewHolder.itemView.findViewById(R.id.notchRight);
                        View notchLeftMask = viewHolder.itemView.findViewById(R.id.notchLeftMask);
                        View notchRightMask = viewHolder.itemView.findViewById(R.id.notchRightMask);

                        if (notchLeft != null && notchRight != null && notchLeftMask != null && notchRightMask != null) {
                            int defaultColor = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.bg_screen);
                            notchLeft.setCardBackgroundColor(defaultColor);
                            notchLeftMask.setBackgroundColor(defaultColor);
                            notchRight.setCardBackgroundColor(defaultColor);
                            notchRightMask.setBackgroundColor(defaultColor);
                        }

                        if (adapter != null && pinnedJourneyManager != null) {
                            pinnedJourneyManager.saveAllPinnedJourneys(adapter.getItems());
                        }
                    }
                });

        touchHelper.attachToRecyclerView(recyclerPinnedJourneys);
    }

    private void drawSwipeBackground(android.graphics.Canvas c, View itemView, float dX) {
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setColor(android.graphics.Color.parseColor("#FFB4AB"));
        paint.setAntiAlias(true);

        float density = itemView.getResources().getDisplayMetrics().density;
        float cornerRadius = 30 * density;
        float horizontalMargin = 16 * density;
        float bottomMargin = 12 * density;

        android.graphics.RectF background = new android.graphics.RectF(
                itemView.getLeft() + horizontalMargin,
                itemView.getTop(),
                itemView.getRight() - horizontalMargin,
                itemView.getBottom() - bottomMargin
        );

        c.drawRoundRect(background, cornerRadius, cornerRadius, paint);

        android.graphics.drawable.Drawable icon = androidx.core.content.ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_menu_delete);
        if (icon != null) {
            icon.setTint(android.graphics.Color.parseColor("#690005"));

            int backgroundHeight = (int) (background.bottom - background.top);
            int iconTop = (int) background.top + (backgroundHeight - icon.getIntrinsicHeight()) / 2;
            int iconBottom = iconTop + icon.getIntrinsicHeight();
            int iconMargin = (int) (16 * density);

            if (dX > 0) {
                int iconLeft = (int) background.left + iconMargin;
                int iconRight = iconLeft + icon.getIntrinsicWidth();
                icon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                icon.draw(c);
            } else if (dX < 0) {
                int iconRight = (int) background.right - iconMargin;
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
        List<Toc> nationalRail = new ArrayList<>();
        List<Toc> tflServices = new ArrayList<>();

        for (Toc toc : visibleTocs) {
            if (toc.delayRepayUrl != null && !toc.delayRepayUrl.isEmpty()) {
                String manager = TOCBrandHelper.getManagerForToc(toc.name);
                boolean isTfl = "tfl".equalsIgnoreCase(manager) ||
                        "tfl-national".equalsIgnoreCase(manager) ||
                        "london buses".equalsIgnoreCase(manager);
                if (isTfl) {
                    tflServices.add(toc);
                } else {
                    nationalRail.add(toc);
                }
            }
        }

        List<Toc> validTocs = new ArrayList<>(nationalRail);
        if (!nationalRail.isEmpty() && !tflServices.isEmpty()) {
            Toc separator = new Toc();
            separator.code = "SEPARATOR";
            separator.name = "Transport for London";
            validTocs.add(separator);
        }
        validTocs.addAll(tflServices);

        recyclerDelayRepay.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerDelayRepay.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {

            private static final int TYPE_ITEM = 0;
            private static final int TYPE_SEPARATOR = 1;

            @Override
            public int getItemViewType(int position) {
                if ("SEPARATOR".equals(validTocs.get(position).code)) {
                    return TYPE_SEPARATOR;
                }
                return TYPE_ITEM;
            }

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                if (viewType == TYPE_SEPARATOR) {
                    View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_separator, parent, false);
                    return new RecyclerView.ViewHolder(v) {};
                } else {
                    View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_disruption, parent, false);
                    return new RecyclerView.ViewHolder(v) {};
                }
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                Toc toc = validTocs.get(position);

                if (getItemViewType(position) == TYPE_SEPARATOR) {
                    TextView tvSeparatorTitle = holder.itemView.findViewById(R.id.tvSeparatorTitle);
                    if (tvSeparatorTitle != null) tvSeparatorTitle.setText(toc.name);

                    ImageView ivSeparatorArrow = holder.itemView.findViewById(R.id.ivSeparatorArrow);
                    if (ivSeparatorArrow != null) ivSeparatorArrow.setVisibility(View.GONE);

                    holder.itemView.setOnClickListener(null);
                    return;
                }

                TextView tvName = holder.itemView.findViewById(R.id.tvTocName);
                TextView tvCode = holder.itemView.findViewById(R.id.tvTocCode);
                TextView tvStatus = holder.itemView.findViewById(R.id.tvStatus);

                tvName.setText(toc.name);
                tvCode.setText(toc.acronym != null ? toc.acronym : toc.code);

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
        loadSavedRoutes();
    }

    private void loadPinnedJourneys() {
        if (pinnedJourneyManager == null) return;

        List<JourneyOption> savedJourneys = pinnedJourneyManager.getPinnedJourneys();

        if (savedJourneys != null) {
            adapter = new PinnedJourneyAdapter(requireContext(), savedJourneys);
            recyclerPinnedJourneys.setAdapter(adapter);
        }

        updateEmptyState();
    }

    private void updateEmptyState() {
        boolean hasPinned = adapter != null && adapter.getItemCount() > 0;
        boolean hasRoutes = savedRoutesManager != null
                && !savedRoutesManager.getSavedRoutes().isEmpty();
        tvListHeader.setVisibility(hasPinned ? View.VISIBLE : View.GONE);
        recyclerPinnedJourneys.setVisibility(hasPinned ? View.VISIBLE : View.GONE);
        layoutEmptyState.setVisibility(!hasPinned && !hasRoutes ? View.VISIBLE : View.GONE);
    }

    private void loadSavedRoutes() {
        if (savedRoutesManager == null || recyclerSavedRoutes == null) return;

        List<SavedRoute> savedRoutes = new ArrayList<>(savedRoutesManager.getSavedRoutes());
        boolean hasRoutes = !savedRoutes.isEmpty();
        if (tvSavedRoutesHeader != null) {
            tvSavedRoutesHeader.setVisibility(hasRoutes ? View.VISIBLE : View.GONE);
        }

        if (hasRoutes) {
            recyclerSavedRoutes.setVisibility(View.VISIBLE);

            SavedRoutesAdapter savedAdapter = new SavedRoutesAdapter(
                    savedRoutes,
                    route -> {
                        Intent intent = new Intent(requireContext(), JourneySearchActivity.class);
                        intent.putExtra("AUTO_SAVED_ROUTE_JSON", new Gson().toJson(route));
                        startActivity(intent);
                    },
                    this::deleteSavedRoute
            );

            recyclerSavedRoutes.setAdapter(savedAdapter);
        } else {
            recyclerSavedRoutes.setAdapter(null);
            recyclerSavedRoutes.setVisibility(View.GONE);
        }
        updateEmptyState();
    }
}