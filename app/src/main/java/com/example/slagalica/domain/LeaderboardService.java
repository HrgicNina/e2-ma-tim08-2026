package com.example.slagalica.domain;

import com.example.slagalica.data.LeaderboardRepository;
import com.example.slagalica.model.LeaderboardEntry;

import java.util.List;

public class LeaderboardService {

    public interface ActionCallback {
        void onSuccess();
        void onError(String message);
    }

    public interface LoadCallback {
        void onSuccess(CycleWindow cycle, List<LeaderboardEntry> entries);
        void onError(String message);
    }

    public interface CyclesCallback {
        void onSuccess(List<CycleWindow> cycles);
        void onError(String message);
    }

    public static class CycleWindow {
        public final String id;
        public final long startMs;
        public final long endMs;
        public final String label;

        public CycleWindow(String id, long startMs, long endMs, String label) {
            this.id = id;
            this.startMs = startMs;
            this.endMs = endMs;
            this.label = label;
        }
    }

    private final LeaderboardRepository repository;

    public LeaderboardService() {
        this(new LeaderboardRepository());
    }

    public LeaderboardService(LeaderboardRepository repository) {
        this.repository = repository;
    }

    public long getRefreshIntervalMs() {
        return repository.getRefreshIntervalMs();
    }

    public void loadWeeklyLeaderboard(LoadCallback callback) {
        repository.loadWeeklyLeaderboard(adapt(callback));
    }

    public void loadMonthlyLeaderboard(LoadCallback callback) {
        repository.loadMonthlyLeaderboard(adapt(callback));
    }

    public void loadCycle(String cycleId, LoadCallback callback) {
        repository.loadCycle(cycleId, adapt(callback));
    }

    public void loadCycles(boolean monthly, CyclesCallback callback) {
        repository.loadCycles(monthly, new LeaderboardRepository.CyclesCallback() {
            @Override
            public void onSuccess(List<LeaderboardRepository.CycleWindow> cycles) {
                java.util.ArrayList<CycleWindow> out = new java.util.ArrayList<>();
                for (LeaderboardRepository.CycleWindow cycle : cycles) {
                    out.add(mapCycle(cycle));
                }
                callback.onSuccess(out);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    public void processCycleRolloverAndRewards(ActionCallback callback) {
        repository.processCycleRolloverAndRewards(new LeaderboardRepository.ActionCallback() {
            @Override
            public void onSuccess() {
                callback.onSuccess();
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    private LeaderboardRepository.LoadCallback adapt(LoadCallback callback) {
        return new LeaderboardRepository.LoadCallback() {
            @Override
            public void onSuccess(LeaderboardRepository.CycleWindow cycle, List<LeaderboardEntry> entries) {
                callback.onSuccess(mapCycle(cycle), entries);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        };
    }

    private CycleWindow mapCycle(LeaderboardRepository.CycleWindow cycle) {
        return new CycleWindow(cycle.id, cycle.startMs, cycle.endMs, cycle.label());
    }
}
