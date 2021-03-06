package com.android.sdk.qrcode

import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import io.fotoapparat.Fotoapparat
import io.fotoapparat.configuration.CameraConfiguration
import io.fotoapparat.configuration.UpdateConfiguration
import io.fotoapparat.log.logcat
import io.fotoapparat.preview.Frame
import io.fotoapparat.selector.*
import io.fotoapparat.view.CameraView

abstract class QRCodeView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), ProcessDataTask.Delegate {

    private lateinit var cameraView: CameraView
    private lateinit var fotoapparat: Fotoapparat

    private lateinit var scanBoxView: ScanBoxView
    private var delegate: Delegate? = null

    private var dataTask: ProcessDataTask? = null
    protected var spotAble = false

    private val framingRect = Rect()
    private var framingRectInPreview: Rect? = null

    init {
        initView(context, attrs)
    }

    private fun initView(context: Context, attrs: AttributeSet?) {
        cameraView = CameraView(getContext())
        scanBoxView = ScanBoxView(getContext())
        scanBoxView.initCustomAttrs(context, attrs)
        addView(cameraView)
        addView(scanBoxView)
        try {
            fotoapparat = createFotoapparat()
        } catch (e: Exception) {
            e.printStackTrace()
            delegate?.onScanQRCodeOpenCameraError(e)
        }
    }

    private fun createFotoapparat(): Fotoapparat {
        val configuration = CameraConfiguration(
                previewResolution = firstAvailable(
                        wideRatio(highestResolution()),
                        standardRatio(highestResolution())
                ),
                previewFpsRange = highestFps(),
                flashMode = off(),
                focusMode = firstAvailable(
                        continuousFocusPicture(),
                        autoFocus()
                ),
                frameProcessor = this::processFrame
        )

        return Fotoapparat(
                context = this@QRCodeView.context,
                view = cameraView,
                logger = logcat(),
                lensPosition = back(),
                cameraConfiguration = configuration,
                cameraErrorCallback = {
                    delegate?.onScanQRCodeOpenCameraError(it)
                }
        )
    }

    private fun processFrame(frame: Frame) {
        val processDataTask = dataTask
        if (spotAble && (processDataTask == null || processDataTask.isCancelled)) {

            dataTask = object : ProcessDataTask(frame.image, frame.size, frame.rotation, this) {
                override fun onPostExecute(result: String?) {

                    if (spotAble) {
                        if (!result.isNullOrEmpty()) {
                            try {
                                delegate?.onScanQRCodeSuccess(result)
                                stopSpot()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    cancelProcessDataTask()
                }
            }.perform()
        }
    }

    /**
     * ??????????????????????????????
     *
     * @param delegate ????????????????????????
     */
    fun setDelegate(delegate: Delegate) {
        this.delegate = delegate
    }

    /**
     * ???????????????
     */
    fun showScanRect() {
        scanBoxView.visibility = View.VISIBLE
    }

    /**
     * ???????????????
     */
    fun hiddenScanRect() {
        scanBoxView.visibility = View.GONE
    }

    /**
     * ????????????????????????????????????????????????????????????
     */
    fun startCamera() {
        try {
            fotoapparat.start()
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
        }

    }

    /**
     * ?????????????????????????????????????????????
     */
    fun stopCamera() {
        stopSpotAndHiddenRect()
        try {
            fotoapparat.stop()
        } catch (throwable: Throwable) {
            throwable.printStackTrace()
        }
    }

    /**
     * ???100????????????
     */
    fun startSpot() {
        postDelayed({
            spotAble = true
            startCamera()
        }, 100)
    }

    /**
     * ????????????
     */
    fun stopSpot() {
        cancelProcessDataTask()
        spotAble = false
    }

    /**
     * ????????????????????????????????????
     */
    fun stopSpotAndHiddenRect() {
        stopSpot()
        hiddenScanRect()
    }

    /**
     * ??????????????????????????????1.5??????????????????
     */
    fun startSpotAndShowRect() {
        startSpot()
        showScanRect()
    }

    /**
     * ?????????????????????????????????
     *
     * @return
     */
    val isScanBarcodeStyle: Boolean
        get() = scanBoxView.isBarcode

    /**
     * ???????????????
     */
    fun openFlashlight() {
        try {
            fotoapparat.updateConfiguration(UpdateConfiguration(flashMode = firstAvailable(torch(), off())))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * ???????????????
     */
    fun closeFlashlight() {
        try {
            fotoapparat.updateConfiguration(UpdateConfiguration(flashMode = firstAvailable(off())))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * ???????????????????????????
     */
    fun onDestroy() {
        delegate = null
    }

    /**
     * ????????????????????????
     */
    protected fun cancelProcessDataTask() {
        dataTask?.cancelTask()
        dataTask = null
    }

    /**
     * ???????????????????????????
     */
    fun changeToScanBarcodeStyle() {
        scanBoxView.isBarcode = true
    }

    /**
     * ??????????????????????????????
     */
    fun changeToScanQRCodeStyle() {
        scanBoxView.isBarcode = false
    }

    fun setDebug(debug: Boolean) {
        Debug.setDebug(debug)
    }

    protected fun getFramingRectInPreview(previewWidth: Int, previewHeight: Int): Rect? {
        if (!scanBoxView.getScanBoxAreaRect(framingRect)) {
            return null
        }
        if (framingRectInPreview == null) {
            val rect = Rect(framingRect)
            val cameraResolution = Point(previewWidth, previewHeight)
            val screenResolution = Utils.getScreenResolution(context)
            val x = cameraResolution.x * 1.0f / screenResolution.x
            val y = cameraResolution.y * 1.0f / screenResolution.y
            rect.left = (rect.left * x).toInt()
            rect.right = (rect.right * x).toInt()
            rect.top = (rect.top * y).toInt()
            rect.bottom = (rect.bottom * y).toInt()
            framingRectInPreview = rect
        }
        return framingRectInPreview
    }

    interface Delegate {

        /**
         * ??????????????????
         */
        fun onScanQRCodeSuccess(result: String)

        /**
         * ????????????????????????
         */
        fun onScanQRCodeOpenCameraError(error: Exception)

    }

}