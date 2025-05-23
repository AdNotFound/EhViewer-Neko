/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hippo.ehviewer.ui.fragment

import android.os.Bundle
import androidx.annotation.StringRes
import androidx.preference.Preference
import com.hippo.ehviewer.R
import com.hippo.util.loadHtml

class AboutFragment : BasePreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.about_settings)
        val author = findPreference<Preference>(KEY_AUTHOR)
        author!!.summary = loadHtml(getString(R.string.settings_about_author_summary).replace('$', '@'))
    }

    @get:StringRes
    override val fragmentTitle: Int
        get() = R.string.settings_about

    companion object {
        private const val KEY_AUTHOR = "author"
    }
}
