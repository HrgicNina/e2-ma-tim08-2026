package com.example.slagalica.domain;

import com.example.slagalica.data.PlayerEconomyRepository;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Map;

public class EconomyService {

    public interface EconomyCallback {
        void onSuccess(Map<String, Long> values);
        void onError(String message);
    }

    public interface EconomyObserver {
        void onChanged(Map<String, Long> values);
        void onError(String message);
    }

    private final PlayerEconomyRepository repository;

    public EconomyService() {
        this(new PlayerEconomyRepository());
    }

    public EconomyService(PlayerEconomyRepository repository) {
        this.repository = repository;
    }

    public void getEconomy(String uid, EconomyCallback callback) {
        repository.getEconomy(uid, adapt(callback));
    }

    public void getEconomyByUsername(String username, EconomyCallback callback) {
        repository.getEconomyByUsername(username, adapt(callback));
    }

    public void grantDailyTokensIfNeeded(String uid, EconomyCallback callback) {
        repository.grantDailyTokensIfNeeded(uid, adapt(callback));
    }

    public void reserveTokenForRankedMatch(String uid, EconomyCallback callback) {
        repository.reserveTokenForRankedMatch(uid, adapt(callback));
    }

    public void refundReservedToken(String uid, EconomyCallback callback) {
        repository.refundReservedToken(uid, adapt(callback));
    }

    public void applyRankedMatchResult(String uid, boolean winner, int score, EconomyCallback callback) {
        repository.applyRankedMatchResult(uid, winner, score, adapt(callback));
    }

    public void applyRankedDrawResult(String uid, EconomyCallback callback) {
        repository.applyRankedDrawResult(uid, adapt(callback));
    }

    public void applyForfeitLoserPenalty(String uid, EconomyCallback callback) {
        repository.applyForfeitLoserPenalty(uid, adapt(callback));
    }

    public ListenerRegistration observeEconomy(String uid, EconomyObserver observer) {
        return repository.observeEconomy(uid, new PlayerEconomyRepository.EconomyObserver() {
            @Override
            public void onChanged(Map<String, Long> values) {
                observer.onChanged(values);
            }

            @Override
            public void onError(String message) {
                observer.onError(message);
            }
        });
    }

    private PlayerEconomyRepository.EconomyCallback adapt(EconomyCallback callback) {
        return new PlayerEconomyRepository.EconomyCallback() {
            @Override
            public void onSuccess(Map<String, Long> values) {
                callback.onSuccess(values);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        };
    }
}
