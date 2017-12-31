package fr.free.nrw.commons.category;

import android.os.AsyncTask;
import android.text.TextUtils;
import android.view.View;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;

import fr.free.nrw.commons.CommonsApplication;
import fr.free.nrw.commons.mwapi.MediaWikiApi;
import timber.log.Timber;

/**
 * Sends asynchronous queries to the Commons MediaWiki API to retrieve categories that share the
 * same prefix as the keyword typed in by the user. The 'acprefix' action-specific parameter is used
 * for this purpose. This class should be subclassed in CategorizationFragment.java to aggregate
 * the results.
 */
public class PrefixUpdater extends AsyncTask<Void, Void, List<String>> {

    private String filter;
    private CategorizationFragment catFragment;

    public PrefixUpdater(CategorizationFragment catFragment) {
        this.catFragment = catFragment;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        filter = catFragment.categoriesFilter.getText().toString();
        catFragment.categoriesSearchInProgress.setVisibility(View.VISIBLE);
        catFragment.categoriesNotFoundView.setVisibility(View.GONE);

        catFragment.categoriesSkip.setVisibility(View.GONE);
    }

    /**
     * Remove categories that contain a year in them (starting with 19__ or 20__), except for this year
     * and previous year
     * Rationale: https://github.com/commons-app/apps-android-commons/issues/47
     *
     * @param items Unfiltered list of categories
     * @return Filtered category list
     */
    private List<String> filterYears(List<String> items) {

        Iterator<String> iterator;

        //Check for current and previous year to exclude these categories from removal
        Calendar now = Calendar.getInstance();
        int year = now.get(Calendar.YEAR);
        String yearInString = String.valueOf(year);
        Timber.d("Year: %s", yearInString);

        int prevYear = year - 1;
        String prevYearInString = String.valueOf(prevYear);
        Timber.d("Previous year: %s", prevYearInString);

        //Copy to Iterator to prevent ConcurrentModificationException when removing item
        for (iterator = items.iterator(); iterator.hasNext(); ) {
            String s = iterator.next();

            //Check if s contains a 4-digit word anywhere within the string (.* is wildcard)
            //And that s does not equal the current year or previous year
            if (s.matches(".*(19|20)\\d{2}.*") && !s.contains(yearInString) && !s.contains(prevYearInString)) {
                Timber.d("Filtering out year %s", s);
                iterator.remove();
            }
            
            // Check if the year in the form of XX(X)0s is relevant. If not, delete.
            else if (s.matches(".*0s.*")) {
                Timber.d("This is a candidate for deletion: %s", s);

                if (!s.matches(".*(200|201)0s.*")) {
                    Timber.d("Filtering out this result: %s", s);
                    iterator.remove();
                }
            }
        }

        Timber.d("Items: %s", items);
        return items;
    }

    @Override
    protected List<String> doInBackground(Void... voids) {
        //If user hasn't typed anything in yet, get GPS and recent items
        if (TextUtils.isEmpty(filter)) {
            ArrayList<String> mergedItems = new ArrayList<>(catFragment.mergeItems());
            Timber.d("Merged items, waiting for filter");
            return new ArrayList<>(filterYears(mergedItems));
        }

        //if user types in something that is in cache, return cached category
        if (catFragment.categoriesCache.containsKey(filter)) {
            ArrayList<String> cachedItems = new ArrayList<>(catFragment.categoriesCache.get(filter));
            Timber.d("Found cache items, waiting for filter");
            return new ArrayList<>(filterYears(cachedItems));
        }

        //otherwise if user has typed something in that isn't in cache, search API for matching categories
        //URL: https://commons.wikimedia.org/w/api.php?action=query&list=allcategories&acprefix=filter&aclimit=25
        MediaWikiApi api = CommonsApplication.getInstance().getMWApi();
        List<String> categories = new ArrayList<>();
        try {
            categories = api.allCategories(CategorizationFragment.SEARCH_CATS_LIMIT, this.filter);
            Timber.d("Prefix URL filter %s", categories);
        } catch (IOException e) {
            Timber.e(e, "IO Exception: ");
            //Return empty arraylist
            return categories;
        }

        Timber.d("Found categories from Prefix search, waiting for filter");
        return new ArrayList<>(filterYears(categories));
    }
}
