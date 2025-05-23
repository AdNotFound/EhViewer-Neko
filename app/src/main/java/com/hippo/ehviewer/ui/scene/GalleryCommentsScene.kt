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
package com.hippo.ehviewer.ui.scene

import android.animation.Animator
import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Looper
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import android.util.Log
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.text.getSpans
import androidx.core.text.inSpans
import androidx.core.text.set
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.hippo.app.EditTextDialogBuilder
import com.hippo.easyrecyclerview.EasyRecyclerView
import com.hippo.easyrecyclerview.LinearDividerItemDecoration
import com.hippo.ehviewer.R
import com.hippo.ehviewer.UrlOpener
import com.hippo.ehviewer.WindowInsetsAnimationHelper
import com.hippo.ehviewer.client.EhClient
import com.hippo.ehviewer.client.EhFilter
import com.hippo.ehviewer.client.EhRequest
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.client.data.GalleryComment
import com.hippo.ehviewer.client.data.GalleryCommentList
import com.hippo.ehviewer.client.data.GalleryDetail
import com.hippo.ehviewer.client.data.ListUrlBuilder
import com.hippo.ehviewer.client.parser.VoteCommentParser
import com.hippo.ehviewer.dao.Filter
import com.hippo.ehviewer.ui.MainActivity
import com.hippo.util.ExceptionUtils
import com.hippo.util.ReadableTime
import com.hippo.util.TextUrl
import com.hippo.util.addTextToClipboard
import com.hippo.util.getParcelableCompat
import com.hippo.util.loadHtml
import com.hippo.util.toBBCode
import com.hippo.view.ViewTransition
import com.hippo.widget.FabLayout
import com.hippo.widget.LinkifyTextView
import com.hippo.widget.ObservedTextView
import com.hippo.yorozuya.AnimationUtils
import com.hippo.yorozuya.LayoutUtils
import com.hippo.yorozuya.ResourcesUtils
import com.hippo.yorozuya.SimpleAnimatorListener
import com.hippo.yorozuya.StringUtils
import com.hippo.yorozuya.ViewUtils
import com.hippo.yorozuya.collect.IntList
import rikka.core.res.resolveColor
import kotlin.math.hypot

class GalleryCommentsScene :
    ToolbarScene(),
    View.OnClickListener,
    OnRefreshListener {
    private var mGalleryDetail: GalleryDetail? = null
    private var mRecyclerView: EasyRecyclerView? = null
    private var mFabLayout: FabLayout? = null
    private var mFab: FloatingActionButton? = null
    private var mEditPanel: View? = null
    private var mSendImage: ImageView? = null
    private var mEditText: EditText? = null
    private var mAdapter: CommentAdapter? = null
    private var mViewTransition: ViewTransition? = null
    private var mRefreshLayout: SwipeRefreshLayout? = null
    private var mSendDrawable: Drawable? = null
    private var mPencilDrawable: Drawable? = null
    private var mCommentId: Long = 0
    private var mInAnimation = false
    private var mShowAllComments = false
    private var mRefreshingComments = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            onInit()
        } else {
            onRestore(savedInstanceState)
        }
    }

    private fun handleArgs(args: Bundle?) {
        if (args == null) {
            return
        }
        mGalleryDetail = args.getParcelableCompat(KEY_GALLERY_DETAIL)
        mShowAllComments = mGalleryDetail != null && mGalleryDetail!!.comments != null && !mGalleryDetail!!.comments!!.hasMore
    }

    private fun onInit() {
        handleArgs(arguments)
    }

    private fun onRestore(savedInstanceState: Bundle) {
        mGalleryDetail = savedInstanceState.getParcelableCompat(KEY_GALLERY_DETAIL)
        mShowAllComments = mGalleryDetail != null && mGalleryDetail!!.comments != null && !mGalleryDetail!!.comments!!.hasMore
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(KEY_GALLERY_DETAIL, mGalleryDetail)
    }

    override fun onCreateViewWithToolbar(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.scene_gallery_comments, container, false) as View
        mRecyclerView = ViewUtils.`$$`(view, R.id.recycler_view) as EasyRecyclerView
        val tip = ViewUtils.`$$`(view, R.id.tip) as TextView
        mEditPanel = ViewUtils.`$$`(view, R.id.edit_panel)
        mSendImage = ViewUtils.`$$`(mEditPanel, R.id.send) as ImageView
        mEditText = ViewUtils.`$$`(mEditPanel, R.id.edit_text) as EditText
        mFabLayout = ViewUtils.`$$`(view, R.id.fab_layout) as FabLayout
        mFab = ViewUtils.`$$`(view, R.id.fab) as FloatingActionButton
        mRefreshLayout = ViewUtils.`$$`(view, R.id.refresh_layout) as SwipeRefreshLayout

        ViewCompat.setWindowInsetsAnimationCallback(
            view,
            WindowInsetsAnimationHelper(
                WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_STOP,
                mEditPanel,
                mFabLayout,
            ),
        )
        mRefreshLayout!!.setColorSchemeResources(
            R.color.loading_indicator_red,
            R.color.loading_indicator_purple,
            R.color.loading_indicator_blue,
            R.color.loading_indicator_cyan,
            R.color.loading_indicator_green,
            R.color.loading_indicator_yellow,
        )
        mRefreshLayout!!.setOnRefreshListener(this)
        val context = requireContext()
        val drawable = ContextCompat.getDrawable(context, R.drawable.big_sad_pandroid)
        drawable!!.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        tip.setCompoundDrawables(null, drawable, null, null)
        mSendDrawable = ContextCompat.getDrawable(context, R.drawable.v_send_dark_x24)
        mPencilDrawable = ContextCompat.getDrawable(context, R.drawable.v_pencil_dark_x24)
        mAdapter = CommentAdapter()
        mRecyclerView!!.adapter = mAdapter
        mRecyclerView!!.layoutManager = LinearLayoutManager(
            context,
            RecyclerView.VERTICAL,
            false,
        )
        val decoration = LinearDividerItemDecoration(
            LinearDividerItemDecoration.VERTICAL,
            theme.resolveColor(R.attr.dividerColor),
            LayoutUtils.dp2pix(context, 1.0f),
        )
        decoration.setShowLastDivider(true)
        mRecyclerView!!.addItemDecoration(decoration)
        mRecyclerView!!.setHasFixedSize(true)
        // Cancel change animator
        val itemAnimator = mRecyclerView!!.itemAnimator
        if (itemAnimator is DefaultItemAnimator) {
            itemAnimator.supportsChangeAnimations = false
        }
        mSendImage!!.setOnClickListener(this)
        mEditText!!.customSelectionActionModeCallback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                requireActivity().menuInflater.inflate(R.menu.context_comment, menu)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = true

            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                item?.let {
                    val text = mEditText!!.editableText
                    val start = mEditText!!.selectionStart
                    val end = mEditText!!.selectionEnd
                    when (item.itemId) {
                        R.id.action_bold -> text[start, end] = StyleSpan(Typeface.BOLD)

                        R.id.action_italic -> text[start, end] = StyleSpan(Typeface.ITALIC)

                        R.id.action_underline -> text[start, end] = UnderlineSpan()

                        R.id.action_strikethrough -> text[start, end] = StrikethroughSpan()

                        R.id.action_url -> {
                            val oldSpans = text.getSpans<URLSpan>(start, end)
                            var oldUrl = "https://"
                            oldSpans.forEach {
                                if (!TextUtils.isEmpty(it.url)) {
                                    oldUrl = it.url
                                }
                            }
                            val builder = EditTextDialogBuilder(
                                context,
                                oldUrl,
                                getString(R.string.format_url),
                            )
                            builder.setTitle(getString(R.string.format_url))
                            builder.setPositiveButton(android.R.string.ok, null)
                            val dialog = builder.show()
                            val button: View? = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                            button?.setOnClickListener(
                                View.OnClickListener {
                                    val url = builder.text.trim()
                                    if (TextUtils.isEmpty(url)) {
                                        builder.setError(getString(R.string.text_is_empty))
                                        return@OnClickListener
                                    } else {
                                        builder.setError(null)
                                    }
                                    text.clearSpan(start, end, true)
                                    text[start, end] = URLSpan(url)
                                    dialog.dismiss()
                                },
                            )
                        }

                        R.id.action_clear -> {
                            text.clearSpan(start, end, false)
                        }

                        else -> return false
                    }
                    mode?.finish()
                }
                return true
            }

            override fun onDestroyActionMode(mode: ActionMode?) {
            }
        }
        mFab!!.setOnClickListener(this)
        addAboveSnackView(mEditPanel!!)
        addAboveSnackView(mFabLayout!!)
        mViewTransition = ViewTransition(mRecyclerView, tip)
        updateView(false)
        return view
    }

    fun Spannable.clearSpan(start: Int, end: Int, url: Boolean) {
        val spans = if (url) getSpans<URLSpan>(start, end) else getSpans<CharacterStyle>(start, end)
        spans.forEach {
            val spanStart = getSpanStart(it)
            val spanEnd = getSpanEnd(it)
            removeSpan(it)
            if (spanStart < start) {
                this[spanStart, start] = it
            }
            if (spanEnd > end) {
                this[end, spanEnd] = it
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (null != mRecyclerView) {
            mRecyclerView!!.stopScroll()
            mRecyclerView = null
        }
        if (null != mEditPanel) {
            removeAboveSnackView(mEditPanel!!)
            mEditPanel = null
        }
        if (null != mFabLayout) {
            removeAboveSnackView(mFabLayout!!)
            mFabLayout = null
        }
        mFab = null
        mSendImage = null
        mEditText = null
        mAdapter = null
        mViewTransition = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setTitle(R.string.gallery_comments)
        setNavigationIcon(R.drawable.v_arrow_left_dark_x24)
    }

    override fun onNavigationClick() {
        onBackPressed()
    }

    private fun showFilterCommenterDialog(commenter: String?, position: Int) {
        val context = context
        if (context == null || commenter == null) {
            return
        }
        AlertDialog.Builder(context)
            .setMessage(getString(R.string.filter_the_commenter, commenter))
            .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                val filter = Filter()
                filter.mode = EhFilter.MODE_COMMENTER
                filter.text = commenter
                EhFilter.addFilter(filter)
                hideComment(position)
                showTip(R.string.filter_added, LENGTH_SHORT)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun hideComment(position: Int) {
        if (mGalleryDetail == null || mGalleryDetail!!.comments == null || mGalleryDetail!!.comments!!.comments == null) {
            return
        }
        val oldCommentsList = mGalleryDetail!!.comments!!.comments
        val newCommentsList = arrayOfNulls<GalleryComment>(
            oldCommentsList!!.size - 1,
        )
        var i = 0
        var j = 0
        while (i < oldCommentsList.size) {
            if (i != position) {
                newCommentsList[j] = oldCommentsList[i]
                j++
            }
            i++
        }
        mGalleryDetail!!.comments!!.comments = newCommentsList.requireNoNulls()
        mAdapter!!.notifyDataSetChanged()
        updateView(true)
    }

    private fun voteComment(id: Long, vote: Int) {
        val context = context
        val activity = mainActivity
        if (null == context || null == activity) {
            return
        }
        val request = EhRequest()
            .setMethod(EhClient.METHOD_VOTE_COMMENT)
            .setArgs(
                mGalleryDetail!!.apiUid,
                mGalleryDetail!!.apiKey,
                mGalleryDetail!!.gid,
                mGalleryDetail!!.token,
                id,
                vote,
            )
            .setCallback(VoteCommentListener(context))
        request.enqueue(this)
    }

    @SuppressLint("InflateParams")
    fun showVoteStatusDialog(context: Context?, voteStatus: String?) {
        var mContext = context
        val temp = StringUtils.split(voteStatus, ',')
        val length = temp.size
        val userArray = arrayOfNulls<String>(length)
        val voteArray = arrayOfNulls<String>(length)
        for (i in 0 until length) {
            val str = StringUtils.trim(temp[i])
            val index = str.lastIndexOf(' ')
            if (index < 0) {
                Log.d(TAG, "Something wrong happened about vote state")
                userArray[i] = str
                voteArray[i] = ""
            } else {
                userArray[i] = StringUtils.trim(str.substring(0, index))
                voteArray[i] = StringUtils.trim(str.substring(index + 1))
            }
        }
        val builder = AlertDialog.Builder(mContext!!)
        mContext = builder.context
        val inflater = LayoutInflater.from(mContext)
        val rv = inflater.inflate(R.layout.dialog_recycler_view, null) as EasyRecyclerView
        rv.adapter = object : RecyclerView.Adapter<InfoHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InfoHolder = InfoHolder(inflater.inflate(R.layout.item_drawer_favorites, parent, false))

            override fun onBindViewHolder(holder: InfoHolder, position: Int) {
                holder.key.text = userArray[position]
                holder.value.text = voteArray[position]
            }

            override fun getItemCount(): Int = length
        }
        rv.layoutManager = LinearLayoutManager(mContext)
        val decoration = LinearDividerItemDecoration(
            LinearDividerItemDecoration.VERTICAL,
            theme.resolveColor(R.attr.dividerColor),
            LayoutUtils.dp2pix(mContext, 1.0f),
        )
        decoration.setPadding(ResourcesUtils.getAttrDimensionPixelOffset(mContext, androidx.appcompat.R.attr.dialogPreferredPadding))
        rv.addItemDecoration(decoration)
        rv.clipToPadding = false
        builder.setView(rv).show()
    }

    private fun showCommentDialog(position: Int, text: CharSequence) {
        val context = context
        if (context == null || mGalleryDetail == null || mGalleryDetail!!.comments == null || mGalleryDetail!!.comments!!.comments == null || position >= mGalleryDetail!!.comments!!.comments!!.size || position < 0) {
            return
        }
        val comment = mGalleryDetail!!.comments!!.comments!![position]
        val menu: MutableList<String> = ArrayList()
        val menuId = IntList()
        val resources = context.resources
        menu.add(resources.getString(R.string.copy_comment_text))
        menuId.add(R.id.copy)
        if (!comment.uploader && !comment.editable) {
            menu.add(resources.getString(R.string.block_commenter))
            menuId.add(R.id.block_commenter)
        }
        if (comment.editable) {
            menu.add(resources.getString(R.string.edit_comment))
            menuId.add(R.id.edit_comment)
        }
        if (comment.voteUpAble) {
            menu.add(resources.getString(if (comment.voteUpEd) R.string.cancel_vote_up else R.string.vote_up))
            menuId.add(R.id.vote_up)
        }
        if (comment.voteDownAble) {
            menu.add(resources.getString(if (comment.voteDownEd) R.string.cancel_vote_down else R.string.vote_down))
            menuId.add(R.id.vote_down)
        }
        if (!TextUtils.isEmpty(comment.voteState)) {
            menu.add(resources.getString(R.string.check_vote_status))
            menuId.add(R.id.check_vote_status)
        }
        AlertDialog.Builder(context)
            .setItems(menu.toTypedArray()) { _: DialogInterface?, which: Int ->
                if (which < 0 || which >= menuId.size) {
                    return@setItems
                }
                val id = menuId[which]
                if (id == R.id.copy) {
                    requireActivity().addTextToClipboard(text, false)
                } else if (id == R.id.block_commenter) {
                    showFilterCommenterDialog(comment.user, position)
                } else if (id == R.id.vote_up) {
                    voteComment(comment.id, 1)
                } else if (id == R.id.vote_down) {
                    voteComment(comment.id, -1)
                } else if (id == R.id.check_vote_status) {
                    showVoteStatusDialog(context, comment.voteState)
                } else if (id == R.id.edit_comment) {
                    prepareEditComment(comment.id, text)
                    if (!mInAnimation && mEditPanel != null && mEditPanel!!.visibility != View.VISIBLE) {
                        showEditPanel()
                    }
                }
            }.show()
    }

    fun onItemClick(parent: EasyRecyclerView?, view2: View?, position: Int): Boolean {
        val activity = mainActivity ?: return false
        val holder = parent!!.getChildViewHolder(view2!!)
        if (holder is ActualCommentHolder) {
            val span = holder.comment.currentSpan
            holder.comment.clearCurrentSpan()
            if (span is URLSpan) {
                UrlOpener.openUrl(activity, span.url, true, mGalleryDetail)
            } else {
                showCommentDialog(position, holder.sp)
            }
        } else if (holder is MoreCommentHolder && !mRefreshingComments && mAdapter != null) {
            mRefreshingComments = true
            mShowAllComments = true
            mAdapter!!.notifyItemChanged(position)
            val url = galleryDetailUrl
            if (url != null) {
                // Request
                val request = EhRequest()
                    .setMethod(EhClient.METHOD_GET_GALLERY_DETAIL)
                    .setArgs(url)
                    .setCallback(RefreshCommentListener(activity))
                request.enqueue(this)
            }
        }
        return true
    }

    private fun updateView(animation: Boolean) {
        if (null == mViewTransition) {
            return
        }
        if (mGalleryDetail == null || mGalleryDetail!!.comments == null || mGalleryDetail!!.comments!!.comments == null || mGalleryDetail!!.comments!!.comments!!.isEmpty()) {
            mViewTransition!!.showView(1, animation)
        } else {
            mViewTransition!!.showView(0, animation)
        }
    }

    private fun prepareNewComment() {
        mCommentId = 0
        if (mSendImage != null) {
            mSendImage!!.setImageDrawable(mSendDrawable)
        }
    }

    private fun prepareEditComment(commentId: Long, text: CharSequence) {
        mCommentId = commentId
        mEditText?.setText(text)
        if (mSendImage != null) {
            mSendImage!!.setImageDrawable(mPencilDrawable)
        }
    }

    private fun showEditPanelWithAnimation() {
        if (null == mFab || null == mEditPanel) {
            return
        }
        mInAnimation = true
        mFab!!.translationX = 0.0f
        mFab!!.translationY = 0.0f
        mFab!!.scaleX = 1.0f
        mFab!!.scaleY = 1.0f
        val fabEndX = mEditPanel!!.left + mEditPanel!!.width / 2 - mFab!!.width / 2
        val fabEndY = mEditPanel!!.top + mEditPanel!!.height / 2 - mFab!!.height / 2
        mFab!!.animate().x(fabEndX.toFloat()).y(fabEndY.toFloat()).scaleX(0.0f).scaleY(0.0f)
            .setInterpolator(AnimationUtils.SLOW_FAST_SLOW_INTERPOLATOR)
            .setDuration(300L).setListener(object : SimpleAnimatorListener() {
                override fun onAnimationEnd(animation: Animator) {
                    if (null == mFab || null == mEditPanel) {
                        return
                    }
                    (mFab as View).visibility = View.INVISIBLE
                    mEditPanel!!.visibility = View.VISIBLE
                    val halfW = mEditPanel!!.width / 2
                    val halfH = mEditPanel!!.height / 2
                    val animator = ViewAnimationUtils.createCircularReveal(
                        mEditPanel,
                        halfW,
                        halfH,
                        0f,
                        hypot(halfW.toDouble(), halfH.toDouble()).toFloat(),
                    ).setDuration(300L)
                    animator.addListener(object : SimpleAnimatorListener() {
                        override fun onAnimationEnd(a: Animator) {
                            mInAnimation = false
                        }
                    })
                    animator.start()
                }
            }).start()
    }

    private fun showEditPanel() {
        showEditPanelWithAnimation()
    }

    private fun hideEditPanelWithAnimation() {
        if (null == mFab || null == mEditPanel) {
            return
        }
        mInAnimation = true
        val halfW = mEditPanel!!.width / 2
        val halfH = mEditPanel!!.height / 2
        val animator = ViewAnimationUtils.createCircularReveal(
            mEditPanel,
            halfW,
            halfH,
            hypot(halfW.toDouble(), halfH.toDouble()).toFloat(),
            0.0f,
        ).setDuration(300L)
        animator.addListener(object : SimpleAnimatorListener() {
            override fun onAnimationEnd(a: Animator) {
                if (null == mFab || null == mEditPanel) {
                    return
                }
                if (Looper.myLooper() != Looper.getMainLooper()) {
                    // Some devices may run this block in non-UI thread.
                    // It might be a bug of Android OS.
                    // Check it here to avoid crash.
                    return
                }
                mEditPanel!!.visibility = View.GONE
                (mFab as View).visibility = View.VISIBLE
                val fabStartX = mEditPanel!!.left + mEditPanel!!.width / 2 - mFab!!.width / 2
                val fabStartY = mEditPanel!!.top + mEditPanel!!.height / 2 - mFab!!.height / 2
                mFab!!.x = fabStartX.toFloat()
                mFab!!.y = fabStartY.toFloat()
                mFab!!.scaleX = 0.0f
                mFab!!.scaleY = 0.0f
                mFab!!.rotation = -45.0f
                mFab!!.animate().translationX(0.0f).translationY(0.0f).scaleX(1.0f).scaleY(1.0f)
                    .rotation(0.0f)
                    .setInterpolator(AnimationUtils.SLOW_FAST_SLOW_INTERPOLATOR)
                    .setDuration(300L).setListener(object : SimpleAnimatorListener() {
                        override fun onAnimationEnd(animation: Animator) {
                            mInAnimation = false
                        }
                    }).start()
            }
        })
        animator.start()
    }

    private fun hideEditPanel() {
        hideSoftInput()
        hideEditPanelWithAnimation()
    }

    private val galleryDetailUrl: String?
        get() = if (mGalleryDetail != null && mGalleryDetail!!.gid != -1L && mGalleryDetail!!.token != null) {
            EhUrl.getGalleryDetailUrl(
                mGalleryDetail!!.gid,
                mGalleryDetail!!.token,
                0,
                mShowAllComments,
            )
        } else {
            null
        }

    override fun onClick(v: View) {
        val context = context
        val activity = mainActivity
        if (null == context || null == activity || null == mEditText) {
            return
        }
        if (mFab === v) {
            if (!mInAnimation) {
                prepareNewComment()
                showEditPanel()
            }
        } else if (mSendImage === v) {
            if (!mInAnimation) {
                val comment = mEditText!!.text.toBBCode()
                if (TextUtils.isEmpty(comment)) {
                    // Comment is empty
                    return
                }
                val url = galleryDetailUrl ?: return
                // Request
                val request = EhRequest()
                    .setMethod(EhClient.METHOD_GET_COMMENT_GALLERY)
                    .setArgs(
                        url,
                        comment,
                        if (mCommentId != 0L) mCommentId.toString() else null,
                    )
                    .setCallback(CommentGalleryListener(context, mCommentId))
                request.enqueue(this)
                hideSoftInput()
                hideEditPanel()
            }
        }
    }

    override fun onBackPressed() {
        if (mInAnimation) {
            return
        }
        if (null != mEditPanel && mEditPanel!!.visibility == View.VISIBLE) {
            hideEditPanel()
        } else {
            finish()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun onRefreshGallerySuccess(result: GalleryCommentList?) {
        if (mGalleryDetail == null || mAdapter == null) {
            return
        }
        mRefreshLayout!!.isRefreshing = false
        mRefreshingComments = false
        mGalleryDetail!!.comments = result
        mAdapter!!.notifyDataSetChanged()
        updateView(true)

        val re = Bundle()
        re.putParcelable(KEY_COMMENT_LIST, result)
        setResult(RESULT_OK, re)
    }

    private fun onRefreshGalleryFailure() {
        if (mAdapter == null) {
            return
        }
        mRefreshLayout!!.isRefreshing = false
        mRefreshingComments = false
        val position = mAdapter!!.itemCount - 1
        if (position >= 0) {
            mAdapter!!.notifyItemChanged(position)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun onCommentGallerySuccess(result: GalleryCommentList) {
        if (mGalleryDetail == null || mAdapter == null) {
            return
        }
        mGalleryDetail!!.comments = result
        mAdapter!!.notifyDataSetChanged()
        val re = Bundle()
        re.putParcelable(KEY_COMMENT_LIST, result)
        setResult(RESULT_OK, re)

        // Remove text
        if (mEditText != null) {
            mEditText!!.setText("")
        }
        updateView(true)
    }

    private fun onVoteCommentSuccess(result: VoteCommentParser.Result) {
        if (mAdapter == null || mGalleryDetail!!.comments == null || mGalleryDetail!!.comments!!.comments == null) {
            return
        }
        var position = -1
        var i = 0
        val n = mGalleryDetail!!.comments!!.comments!!.size
        while (i < n) {
            val comment = mGalleryDetail!!.comments!!.comments!![i]
            if (comment.id == result.id) {
                position = i
                break
            }
            i++
        }
        if (-1 == position) {
            Log.d(TAG, "Can't find comment with id " + result.id)
            return
        }

        // Update comment
        val comment = mGalleryDetail!!.comments!!.comments!![position]
        comment.score = result.score
        if (result.expectVote > 0) {
            comment.voteUpEd = 0 != result.vote
            comment.voteDownEd = false
        } else {
            comment.voteDownEd = 0 != result.vote
            comment.voteUpEd = false
        }
        mAdapter!!.notifyItemChanged(position)

        val re = Bundle()
        val comments = mGalleryDetail!!.comments
        re.putParcelable(KEY_COMMENT_LIST, comments)
        setResult(RESULT_OK, re)
    }

    override fun onRefresh() {
        if (!mRefreshingComments && mAdapter != null) {
            val activity = requireActivity() as MainActivity
            mRefreshingComments = true
            val url = galleryDetailUrl
            if (url != null) {
                // Request
                val request = EhRequest()
                    .setMethod(EhClient.METHOD_GET_GALLERY_DETAIL)
                    .setArgs(url)
                    .setCallback(RefreshCommentListener(activity))
                request.enqueue(this)
            }
        }
    }

    private inner class RefreshCommentListener(context: Context) : EhCallback<GalleryCommentsScene?, GalleryDetail>(context) {
        override fun onSuccess(result: GalleryDetail) {
            val scene = this@GalleryCommentsScene
            scene.onRefreshGallerySuccess(result.comments)
        }

        override fun onFailure(e: Exception) {
            val scene = this@GalleryCommentsScene
            scene.onRefreshGalleryFailure()
        }

        override fun onCancel() {}
    }

    private inner class CommentGalleryListener(context: Context, private val mCommentId: Long) : EhCallback<GalleryCommentsScene?, GalleryCommentList>(context) {
        override fun onSuccess(result: GalleryCommentList) {
            showTip(
                if (mCommentId != 0L) R.string.edit_comment_successfully else R.string.comment_successfully,
                LENGTH_SHORT,
            )
            val scene = this@GalleryCommentsScene
            scene.onCommentGallerySuccess(result)
        }

        override fun onFailure(e: Exception) {
            showTip(
                """
    ${content.getString(if (mCommentId != 0L) R.string.edit_comment_failed else R.string.comment_failed)}
    ${ExceptionUtils.getReadableString(e)}
                """.trimIndent(),
                LENGTH_LONG,
            )
        }

        override fun onCancel() {}
    }

    private inner class VoteCommentListener(context: Context) : EhCallback<GalleryCommentsScene?, VoteCommentParser.Result>(context) {
        override fun onSuccess(result: VoteCommentParser.Result) {
            showTip(
                if (result.expectVote > 0) {
                    if (0 != result.vote) R.string.vote_up_successfully else R.string.cancel_vote_up_successfully
                } else if (0 != result.vote) {
                    R.string.vote_down_successfully
                } else {
                    R.string.cancel_vote_down_successfully
                },
                LENGTH_SHORT,
            )
            val scene = this@GalleryCommentsScene
            scene.onVoteCommentSuccess(result)
        }

        override fun onFailure(e: Exception) {
            showTip(R.string.vote_failed, LENGTH_LONG)
        }

        override fun onCancel() {}
    }

    private class InfoHolder(itemView: View?) :
        RecyclerView.ViewHolder(
            itemView!!,
        ) {
        val key: TextView = ViewUtils.`$$`(itemView, R.id.key) as TextView
        val value: TextView = ViewUtils.`$$`(itemView, R.id.value) as TextView
    }

    private abstract class CommentHolder(inflater: LayoutInflater, resId: Int, parent: ViewGroup?) : RecyclerView.ViewHolder(inflater.inflate(resId, parent, false))

    private class MoreCommentHolder(inflater: LayoutInflater, parent: ViewGroup?) : CommentHolder(inflater, R.layout.item_gallery_comment_more, parent)

    private class ProgressCommentHolder(inflater: LayoutInflater, parent: ViewGroup?) : CommentHolder(inflater, R.layout.item_gallery_comment_progress, parent)

    private inner class ActualCommentHolder(inflater: LayoutInflater, parent: ViewGroup?) : CommentHolder(inflater, R.layout.item_gallery_comment, parent) {
        private val user: TextView = itemView.findViewById(R.id.user)
        private val time: TextView = itemView.findViewById(R.id.time)
        val comment: LinkifyTextView = itemView.findViewById(R.id.comment)
        lateinit var sp: CharSequence

        private fun generateComment(
            context: Context,
            textView: ObservedTextView,
            comment: GalleryComment,
        ): CharSequence {
            sp = loadHtml(comment.comment, textView)
            val ssb = SpannableStringBuilder(sp)
            if (0L != comment.id && 0 != comment.score) {
                val score = comment.score
                val scoreString = if (score > 0) "+$score" else score.toString()
                ssb.append("  ").inSpans(
                    RelativeSizeSpan(0.8f),
                    StyleSpan(Typeface.BOLD),
                    ForegroundColorSpan(theme.resolveColor(android.R.attr.textColorSecondary)),
                ) {
                    append(scoreString)
                }
            }
            if (comment.lastEdited != 0L) {
                val str = context.getString(
                    R.string.last_edited,
                    ReadableTime.getTimeAgo(comment.lastEdited),
                )
                ssb.append("\n\n").inSpans(
                    RelativeSizeSpan(0.8f),
                    StyleSpan(Typeface.BOLD),
                    ForegroundColorSpan(theme.resolveColor(android.R.attr.textColorSecondary)),
                ) {
                    append(str)
                }
            }
            return TextUrl.handleTextUrl(ssb)
        }

        fun bind(value: GalleryComment) {
            user.text = if (value.uploader) {
                getString(
                    R.string.comment_user_uploader,
                    value.user,
                )
            } else {
                value.user
            }
            user.setOnClickListener {
                if ("Anonymous" != value.user) {
                    val lub = ListUrlBuilder()
                    lub.mode = ListUrlBuilder.MODE_UPLOADER
                    lub.keyword = value.user
                    GalleryListScene.startScene(this@GalleryCommentsScene, lub)
                }
            }
            time.text = ReadableTime.getTimeAgo(value.time)
            comment.text = generateComment(comment.context, comment, value)
        }
    }

    private inner class CommentAdapter : RecyclerView.Adapter<CommentHolder>() {
        private val mInflater: LayoutInflater = layoutInflater

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentHolder = when (viewType) {
            TYPE_COMMENT -> ActualCommentHolder(mInflater, parent)
            TYPE_MORE -> MoreCommentHolder(mInflater, parent)
            TYPE_PROGRESS -> ProgressCommentHolder(mInflater, parent)
            else -> throw IllegalStateException("Invalid view type: $viewType")
        }

        override fun onBindViewHolder(holder: CommentHolder, position: Int) {
            val context = context
            if (context == null || mGalleryDetail == null || mGalleryDetail!!.comments == null) {
                return
            }
            holder.itemView.setOnClickListener {
                onItemClick(
                    mRecyclerView,
                    holder.itemView,
                    position,
                )
            }
            holder.itemView.isClickable = true
            holder.itemView.isFocusable = true
            if (holder is ActualCommentHolder) {
                holder.bind(mGalleryDetail!!.comments!!.comments!![position])
            }
        }

        override fun getItemCount(): Int = if (mGalleryDetail == null || mGalleryDetail!!.comments == null || mGalleryDetail!!.comments!!.comments == null) {
            0
        } else if (mGalleryDetail!!.comments!!.hasMore) {
            mGalleryDetail!!.comments!!.comments!!.size + 1
        } else {
            mGalleryDetail!!.comments!!.comments!!.size
        }

        override fun getItemViewType(position: Int): Int = if (position >= mGalleryDetail!!.comments!!.comments!!.size) {
            if (mRefreshingComments) TYPE_PROGRESS else TYPE_MORE
        } else {
            TYPE_COMMENT
        }
    }

    companion object {
        val TAG: String = GalleryCommentsScene::class.java.simpleName
        const val KEY_API_UID = "api_uid"
        const val KEY_API_KEY = "api_key"
        const val KEY_GID = "gid"
        const val KEY_TOKEN = "token"
        const val KEY_COMMENT_LIST = "comment_list"
        const val KEY_GALLERY_DETAIL = "gallery_detail"
        private const val TYPE_COMMENT = 0
        private const val TYPE_MORE = 1
        private const val TYPE_PROGRESS = 2
    }
}
