package com.example.slagalica.model;

import java.util.List;

public class ConnectionRound {
    private final String title;
    private final List<String> leftItems;
    private final List<String> rightItems;
    private final List<Integer> mapping;

    public ConnectionRound(String title, List<String> leftItems, List<String> rightItems, List<Integer> mapping) {
        this.title = title;
        this.leftItems = leftItems;
        this.rightItems = rightItems;
        this.mapping = mapping;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getLeftItems() {
        return leftItems;
    }

    public List<String> getRightItems() {
        return rightItems;
    }

    public List<Integer> getMapping() {
        return mapping;
    }
}
