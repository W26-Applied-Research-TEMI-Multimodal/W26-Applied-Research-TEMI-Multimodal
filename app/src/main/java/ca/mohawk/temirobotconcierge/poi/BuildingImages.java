package ca.mohawk.temirobotconcierge.poi;

import java.util.HashMap;
import java.util.Map;

import ca.mohawk.temirobotconcierge.R;

public class BuildingImages {
    public static final Map<String, Integer> BUILDING_TO_DRAWABLE = new HashMap<String, Integer>() {{
        put("EA", R.drawable.ea);
        put("I", R.drawable.i);
        put("G", R.drawable.g);
        put("R", R.drawable.r);
        put("E", R.drawable.e);
        put("M", R.drawable.m);
        put("N", R.drawable.n);
        put("Q", R.drawable.q);
        put("J", R.drawable.j);
        put("A", R.drawable.a);
        put("H", R.drawable.h);
        put("B", R.drawable.b);
        put("C", R.drawable.c);
        put("F", R.drawable.f);
    }};

    public static Integer getDrawableForBuilding(String buildingId) {
        if (buildingId == null) return null;
        return BUILDING_TO_DRAWABLE.get(buildingId.toUpperCase());
    }
}