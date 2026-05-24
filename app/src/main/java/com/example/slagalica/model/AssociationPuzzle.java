package com.example.slagalica.model;

import java.util.List;

public class AssociationPuzzle {
    private final String title;
    private final List<List<String>> columns;
    private final List<String> columnSolutions;
    private final String finalSolution;

    public AssociationPuzzle(String title, List<List<String>> columns, List<String> columnSolutions, String finalSolution) {
        this.title = title;
        this.columns = columns;
        this.columnSolutions = columnSolutions;
        this.finalSolution = finalSolution;
    }

    public String getTitle() {
        return title;
    }

    public List<List<String>> getColumns() {
        return columns;
    }

    public List<String> getColumnSolutions() {
        return columnSolutions;
    }

    public String getFinalSolution() {
        return finalSolution;
    }
}
