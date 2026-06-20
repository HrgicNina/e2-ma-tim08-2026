package com.example.slagalica.domain;

import com.example.slagalica.data.RegionsRepository;
import com.example.slagalica.model.RegionMapData;

public class RegionsService {
    public interface LoadCallback {
        void onSuccess(RegionMapData data);
        void onError(String message);
    }

    public interface ActionCallback {
        void onSuccess();
        void onError(String message);
    }

    private final RegionsRepository repository;

    public RegionsService() {
        this(new RegionsRepository());
    }

    public RegionsService(RegionsRepository repository) {
        this.repository = repository;
    }

    public void loadRegionMap(String myUid, LoadCallback callback) {
        repository.loadRegionMap(myUid, new RegionsRepository.LoadCallback() {
            @Override
            public void onSuccess(RegionMapData data) {
                callback.onSuccess(data);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    public void processPreviousMonthlyRegionAwards(ActionCallback callback) {
        repository.processPreviousMonthlyRegionAwards(new RegionsRepository.ActionCallback() {
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
}
