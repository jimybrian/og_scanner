package com.dzworks.ogscanner.ui

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.*
import android.media.Image
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Size
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import com.dzworks.ogscanner.R
import com.dzworks.ogscanner.databinding.FragmentHomeBinding
import com.google.android.gms.tasks.Task
import com.google.common.util.concurrent.ListenableFuture
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import com.google.firebase.ml.vision.text.FirebaseVisionText
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer
import timber.log.Timber
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class HomeFragment : Fragment() {

    lateinit var binding: FragmentHomeBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_home, container, false)

        return binding.root
    }

    val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()){
            isGranted ->
        if(isGranted){
            startCamera()
        }else
            Toast.makeText(activity, "Please give required permissions", Toast.LENGTH_SHORT).show()
    }

    var textView: TextView? = null
    var mCameraView: PreviewView? = null
    var holder: SurfaceHolder? = null
    var surfaceView: SurfaceView? = null
    var canvas: Canvas? = null
    var paint: Paint? = null
    var cameraHeight:Int = 0
    var cameraWidth: Int = 0
    var xOffset: Int = 0
    var yOffset: Int = 0

    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    /**
     * Responsible for converting the rotation degrees from CameraX into the one compatible with Firebase ML
     */
    private fun degreesToFirebaseRotation(degrees: Int): Int {
        return when (degrees) {
            0 -> FirebaseVisionImageMetadata.ROTATION_0
            90 -> FirebaseVisionImageMetadata.ROTATION_90
            180 -> FirebaseVisionImageMetadata.ROTATION_180
            270 -> FirebaseVisionImageMetadata.ROTATION_270
            else -> throw IllegalArgumentException(
                "Rotation must be 0, 90, 180, or 270."
            )
        }
    }


    /**
     * Starting Camera
     */
    fun startCamera() {
        mCameraView = binding.previewView
        cameraProviderFuture = ProcessCameraProvider.getInstance(context!!)
        cameraProviderFuture!!.addListener({
            try {
                val cameraProvider = cameraProviderFuture!!.get()
                bindPreview(cameraProvider)
            } catch (e: Exception) {
                // No errors need to be handled for this Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(context!!))
    }

    /**
     *
     * Binding to camera
     */
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val preview: Preview = Preview.Builder().build()
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()
        preview.setSurfaceProvider(mCameraView!!.createSurfaceProvider())

        //Image Analysis Function
        //Set static size according to your device or write a dynamic function for it
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(720, 1488))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        imageAnalysis.setAnalyzer(executor, ImageAnalysis.Analyzer { image ->
            //changing normal degrees into Firebase rotation
            val rotationDegrees = degreesToFirebaseRotation(image.imageInfo.rotationDegrees)
            if (image.image == null) {
                return@Analyzer
            }
            //Getting a FirebaseVisionImage object using the Image object and rotationDegrees
            val mediaImage: Image? = image.image
            val images: FirebaseVisionImage =
                FirebaseVisionImage.fromMediaImage(mediaImage!!, rotationDegrees)
            //Getting bitmap from FirebaseVisionImage Object
            val bmp: Bitmap = images.bitmap
            //Getting the values for cropping
            val displaymetrics = DisplayMetrics()
            activity?.windowManager?.defaultDisplay?.getMetrics(displaymetrics)
            val height = bmp.height
            val width = bmp.width
            val left: Int
            val right: Int
            val top: Int
            val bottom: Int
            var diameter: Int
            diameter = width
            if (height < width) {
                diameter = height
            }
            val offset = (0.05 * diameter).toInt()
            diameter -= offset
            left = width / 2 - diameter / 3
            top = height / 2 - diameter / 3
            right = width / 2 + diameter / 3
            bottom = height / 2 + diameter / 3
            xOffset = left
            yOffset = top

            //Creating new cropped bitmap
//            val bitmap = Bitmap.createBitmap(bmp, left, top, width, height)
            //initializing FirebaseVisionTextRecognizer object
            val detector: FirebaseVisionTextRecognizer = FirebaseVision.getInstance().onDeviceTextRecognizer
            //Passing FirebaseVisionImage Object created from the cropped bitmap
            val result: Task<FirebaseVisionText> =
                detector.processImage(FirebaseVisionImage.fromBitmap(bmp))
                    .addOnSuccessListener { firebaseVisionText -> // Task completed successfully
                        textView = binding.txSelectedText
                        //getting decoded text
                        val text: String = firebaseVisionText?.text.orEmpty()
                        //Setting the decoded text in the texttview
                        textView!!.text = text.filter { it.isDigit() }.take(6)
                        //for getting blocks and line elements
                        for (block in firebaseVisionText?.textBlocks.orEmpty()) {
                            val blockText: String = block.text
                            for (line in block.lines) {
                                val lineText: String = line.text
                                for (element in line.elements) {
                                    val elementText: String = element.text
                                }
                            }
                        }
                        image.close()
                    }
                    .addOnFailureListener { e -> // Task failed with an exception
                        Timber.e(e)
                        image.close()
                    }
        })
        val camera: Camera = cameraProvider.bindToLifecycle(
            (this as LifecycleOwner),
            cameraSelector,
            imageAnalysis,
            preview
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //Start Camera
        permissionRequest.launch(Manifest.permission.CAMERA)

        //Create the bounding box
        surfaceView = binding.overlay
        surfaceView!!.setZOrderOnTop(true)
        holder = surfaceView!!.holder
        holder?.setFormat(PixelFormat.TRANSPARENT)
        holder?.addCallback(object: SurfaceHolder.Callback{
            /**
             * Callback functions for the surface Holder*/
            override fun surfaceCreated(p0: SurfaceHolder) = Unit

            override fun surfaceChanged(p0: SurfaceHolder, p1: Int, p2: Int, p3: Int) {
                drawFocusRect(Color.parseColor("#b3dabb"))
            }

            override fun surfaceDestroyed(p0: SurfaceHolder) = Unit
        })

    }

    /**
     *
     * For drawing the rectangular box
     */
    private fun drawFocusRect(color: Int) {
        val displaymetrics = DisplayMetrics()
        activity?.windowManager?.defaultDisplay?.getMetrics(displaymetrics)
        val height = mCameraView!!.height
        val width = mCameraView!!.width

        //cameraHeight = height;
        //cameraWidth = width;
        val left: Int
        val right: Int
        val top: Int
        val bottom: Int
        var diameter: Int
        diameter = width
        if (height < width) {
            diameter = height
        }
        val offset = (0.05 * diameter).toInt()
        diameter -= offset
        canvas = holder!!.lockCanvas()
        canvas?.drawColor(0, PorterDuff.Mode.CLEAR)
        //border's properties
        paint = Paint()
        paint?.style = Paint.Style.STROKE
        paint?.color = color
        paint?.strokeWidth = 5F
        left = width / 2 - diameter / 3
        top = height / 2 - diameter / 3
        right = width / 2 + diameter / 3
        bottom = height / 2 + diameter / 3
        xOffset = left
        yOffset = top
        //Changing the value of x in diameter/x will change the size of the box ; inversely proportionate to x
        canvas?.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), paint!!)
        holder!!.unlockCanvasAndPost(canvas)
    }

}