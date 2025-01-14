package com.github.premnirmal.ticker.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.github.premnirmal.ticker.AppPreferences
import com.github.premnirmal.ticker.AppPreferences.Companion.toCommaSeparatedString
import com.github.premnirmal.ticker.components.Injector
import com.github.premnirmal.ticker.model.IStocksProvider
import com.github.premnirmal.ticker.network.data.Quote
import com.github.premnirmal.tickerwidget.R
import javax.inject.Inject

class WidgetData {

  companion object {
    private const val SORTED_STOCK_LIST = AppPreferences.SORTED_STOCK_LIST
    private const val PREFS_NAME_PREFIX = "stocks_widget_"
    private const val WIDGET_NAME = "WIDGET_NAME"
    private const val LAYOUT_TYPE = AppPreferences.LAYOUT_TYPE
    private const val WIDGET_SIZE = AppPreferences.WIDGET_SIZE
    private const val BOLD_CHANGE = AppPreferences.BOLD_CHANGE
    private const val SHOW_CURRENCY = AppPreferences.SHOW_CURRENCY
    private const val PERCENT = AppPreferences.PERCENT
    private const val AUTOSORT = AppPreferences.SETTING_AUTOSORT
    private const val HIDE_HEADER = AppPreferences.SETTING_HIDE_HEADER

    enum class ChangeType {
      Value,
      Percent
    }
  }

  @Inject internal lateinit var stocksProvider: IStocksProvider
  @Inject internal lateinit var context: Context
  @Inject internal lateinit var widgetDataProvider: WidgetDataProvider
  @Inject internal lateinit var appPreferences: AppPreferences

  private val position: Int
  val widgetId: Int
  private val tickerList: MutableList<String>
  private val preferences: SharedPreferences

  constructor(
    position: Int,
    widgetId: Int
  ) {
    this.position = position
    this.widgetId = widgetId
    Injector.appComponent.inject(this)
    val prefsName = "$PREFS_NAME_PREFIX$widgetId"
    preferences = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    val tickerListVars = preferences.getString(SORTED_STOCK_LIST, "")
    tickerList = if (tickerListVars.isNullOrEmpty()) {
      ArrayList()
    } else {
      ArrayList(
          listOf(
              *tickerListVars.split(",".toRegex()).dropLastWhile(String::isEmpty).toTypedArray()
          )
      )
    }
    save()
  }

  constructor(
    position: Int,
    widgetId: Int,
    isFirstWidget: Boolean
  ) : this(position, widgetId) {
    if (isFirstWidget && tickerList.isEmpty()) {
      addAllFromStocksProvider()
    }
  }

  val nightMode: Boolean
    get() = appPreferences.nightMode == AppCompatDelegate.MODE_NIGHT_YES

  val positiveTextColor: Int
    @ColorRes get() = if (nightMode) R.color.text_widget_positive_dark else R.color.text_widget_positive

  val negativeTextColor: Int
    @ColorRes get() = R.color.text_widget_negative

  fun widgetName(): String {
    var name = preferences.getString(WIDGET_NAME, "")!!
    if (name.isEmpty()) {
      name = "Widget #$position"
      setWidgetName(name)
    }
    return name
  }

  fun setWidgetName(value: String) {
    preferences.edit()
        .putString(WIDGET_NAME, value)
        .apply()
  }

  fun changeType(): ChangeType {
    val state = preferences.getBoolean(PERCENT, false)
    return if (state) ChangeType.Percent else ChangeType.Value
  }

  fun flipChange() {
    val state = preferences.getBoolean(PERCENT, false)
    preferences.edit()
        .putBoolean(PERCENT, !state)
        .apply()
  }


  fun widgetSizePref(): Int = preferences.getInt(WIDGET_SIZE, 0)

  fun setWidgetSizePref(value: Int) {
    preferences.edit()
            .putInt(WIDGET_SIZE, value)
            .apply()
  }

  fun layoutPref(): Int = preferences.getInt(LAYOUT_TYPE, 0)

  fun setLayoutPref(value: Int) {
    preferences.edit()
        .putInt(LAYOUT_TYPE, value)
        .apply()
  }

  @ColorInt fun textColor(): Int {
    return if (nightMode) {
      ContextCompat.getColor(context, R.color.dark_widget_text)
    } else {
      ContextCompat.getColor(context, R.color.widget_text)
    }
  }

  @LayoutRes fun stockViewLayout(): Int {
    return when (layoutPref()) {
      0 -> R.layout.stockview
      1 -> R.layout.stockview2
      2 -> R.layout.stockview3
      else -> R.layout.stockview4
    }
  }

  @DrawableRes
  fun backgroundResource(): Int {
    return if (nightMode) {
      R.drawable.app_widget_background_dark
    } else {
      R.drawable.app_widget_background
    }
  }

  fun autoSortEnabled(): Boolean = preferences.getBoolean(AUTOSORT, false)

  fun setAutoSort(autoSort: Boolean) {
    preferences.edit()
        .putBoolean(AUTOSORT, autoSort)
        .apply()
  }

  fun hideHeader(): Boolean = preferences.getBoolean(HIDE_HEADER, false)

  fun setHideHeader(hide: Boolean) {
    preferences.edit()
        .putBoolean(HIDE_HEADER, hide)
        .apply()
  }

  fun isBoldEnabled(): Boolean = preferences.getBoolean(BOLD_CHANGE, false)

  fun setBoldEnabled(value: Boolean) {
    preferences.edit()
        .putBoolean(BOLD_CHANGE, value)
        .apply()
  }

  fun isCurrencyEnabled(): Boolean = preferences.getBoolean(SHOW_CURRENCY, false)

  fun setCurrencyEnabled(value: Boolean) {
    preferences.edit()
        .putBoolean(SHOW_CURRENCY, value)
        .apply()
  }

  fun getStocks(): List<Quote> {
    val quoteList = ArrayList<Quote>()
    tickerList.map { stocksProvider.getStock(it) }
        .forEach { quote -> quote?.let { quoteList.add(it) } }
    if (autoSortEnabled()) {
      quoteList.sort()
    }
    return quoteList
  }

  fun getTickers(): List<String> = tickerList

  fun hasTicker(symbol: String): Boolean {
    synchronized(tickerList) {
      var found = false
      val toRemove: MutableList<String> = ArrayList()
      for (ticker in tickerList) {
        if (!stocksProvider.hasTicker(ticker)) {
          toRemove.add(ticker)
        } else {
          if (ticker == symbol) {
            found = true
          }
        }
      }
      tickerList.removeAll(toRemove)
      return found
    }
  }

  fun rearrange(tickers: List<String>) {
    synchronized(tickerList) {
      tickerList.clear()
      tickerList.addAll(tickers)
      save()
    }
  }

  fun addTicker(ticker: String) {
    synchronized(tickerList) {
      if (!tickerList.contains(ticker)) {
        tickerList.add(ticker)
      }
      stocksProvider.addStock(ticker)
      save()
    }
  }

  fun addTickers(tickers: List<String>) {
    synchronized(tickerList) {
      val filtered = tickers.filter { !tickerList.contains(it) }
      tickerList.addAll(filtered)
      stocksProvider.addStocks(filtered.filter { !stocksProvider.hasTicker(it) })
      save()
    }
  }

  fun removeStock(ticker: String) {
    synchronized(tickerList) {
      tickerList.remove(ticker)
    }
    if (!widgetDataProvider.containsTicker(ticker)) {
      stocksProvider.removeStock(ticker)
    }
    save()
  }

  fun addAllFromStocksProvider() {
    addTickers(stocksProvider.getTickers())
  }

  fun onWidgetRemoved() {
    preferences.edit()
        .clear()
        .apply()
  }

  private fun save() {
    synchronized(tickerList) {
      preferences.edit()
          .putString(SORTED_STOCK_LIST, tickerList.toCommaSeparatedString())
          .apply()
    }
  }
}