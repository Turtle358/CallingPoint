package io.github.turtle358.callingpoint.ui.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import io.github.turtle358.callingpoint.ui.fragments.DisruptionsFragment;
import io.github.turtle358.callingpoint.ui.fragments.JourneyPlannerFragment;
import io.github.turtle358.callingpoint.ui.fragments.LiveBoardFragment;
import io.github.turtle358.callingpoint.ui.fragments.SettingsFragment;

public class MainPagerAdapter extends FragmentStateAdapter {

    public MainPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return new LiveBoardFragment();
            case 1:
                return new JourneyPlannerFragment();
            case 2:
                return new DisruptionsFragment();
            case 3:
                return new SettingsFragment();
            default:
                return new LiveBoardFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 4;
    }
}