package com.devsocial.model;

import java.util.List;

public final class Categories {
    public static final List<String> ALL = List.of(
            "C++", "Java", "DSA", "Linux", "Operating Systems", "Web Development", "AI/ML", "DevOps", "Tech", "Memes");

    /** How many categories one post can have. */
    public static final int MAX_PER_POST = 3;

    private Categories() {}
}
