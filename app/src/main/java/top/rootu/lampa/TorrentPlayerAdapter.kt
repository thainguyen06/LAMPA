package top.rootu.lampa

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Adapter for displaying torrent player options including LAMPA built-in player
 * and available external apps
 */
class TorrentPlayerAdapter internal constructor(
    private val context: Context,
    private val externalApps: List<ResolveInfo>
) : BaseAdapter() {
    private val mLayoutInflater: LayoutInflater = LayoutInflater.from(context)
    private val pm: PackageManager = context.packageManager

    // Total count is 1 (LAMPA) + external apps
    override fun getCount(): Int {
        return 1 + externalApps.size
    }

    override fun getItem(position: Int): Any {
        return if (position == 0) {
            "LAMPA" // First item is always LAMPA
        } else {
            externalApps[position - 1]
        }
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        var view = convertView
        if (view == null)
            view = mLayoutInflater.inflate(R.layout.app_list_item, null)
        
        val image = view?.findViewById<ImageView>(R.id.imageViewIcon)
        val textViewMain = view?.findViewById<TextView>(R.id.textViewMain)
        val textViewSecond = view?.findViewById<TextView>(R.id.textViewSecond)

        if (position == 0) {
            // LAMPA built-in player
            image?.setImageDrawable(getLampaIcon())
            textViewMain?.text = context.getString(R.string.torrent_player_lampa)
            textViewSecond?.text = context.getString(R.string.app_name).lowercase(Locale.getDefault())
        } else {
            // External app
            val appInfo = externalApps[position - 1]
            image?.setImageDrawable(appInfo.loadIcon(pm))
            textViewMain?.text = getAppLabel(appInfo)
            textViewSecond?.text = appInfo.activityInfo.packageName.lowercase(Locale.getDefault())
        }
        
        return view!!
    }

    private fun getLampaIcon(): Drawable? {
        return ContextCompat.getDrawable(context, R.drawable.lampa_logo_round)
    }

    private fun getAppLabel(appInfo: ResolveInfo): String {
        val loadLabel = appInfo.loadLabel(pm)
        var label = ""
        if (loadLabel == null || loadLabel.toString().also { label = it }.isEmpty()) {
            label = appInfo.activityInfo.packageName.lowercase(Locale.getDefault())
        }
        return label
    }

    /**
     * Get package name for external app at given position
     * Position 0 is LAMPA, so external apps start at position 1
     */
    fun getExternalAppPackage(position: Int): String {
        return if (position > 0) {
            externalApps[position - 1].activityInfo.packageName.lowercase(Locale.getDefault())
        } else {
            ""
        }
    }
}
