package com.softbankrobotics.dx.peppermyocontrol

import android.os.Bundle
import android.util.Log
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.design.activity.RobotActivity
import com.thalmic.myo.scanner.ScanActivity
import android.content.Intent
import androidx.core.content.ContextCompat
import com.thalmic.myo.*
import kotlinx.android.synthetic.main.activity_main.*
import android.Manifest.permission.ACCESS_COARSE_LOCATION
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.`object`.actuation.*
import com.aldebaran.qi.sdk.`object`.geometry.Transform
import com.aldebaran.qi.sdk.builder.TransformBuilder
import com.aldebaran.qi.sdk.builder.GoToBuilder
import com.aldebaran.qi.sdk.builder.LookAtBuilder
import com.aldebaran.qi.sdk.builder.SayBuilder


const val TAG="MyoControlMain"

class MainActivity :  RobotActivity(), RobotLifecycleCallbacks {

    //Myo variables
    private lateinit var listener: AbstractDeviceListener
    private lateinit var hub: Hub
    private var controlEnabled = false
    //Motion variables
    private lateinit var goTo: GoTo
    private lateinit var lookAt: LookAt
    // Store the action execution future.
    private var goToFuture:Future<Void>? = null
    private var lookAtFuture:Future<Void>? = null
    private lateinit var actuation: Actuation
    private lateinit var robotFrame: Frame
    private lateinit var transform: Transform
    private lateinit var mapping: Mapping
    private lateinit var targetFrame: FreeFrame
    private val lookLeft = com.aldebaran.qi.sdk.`object`.geometry.Vector3(-1.0,-0.5,0.0)
    private val lookRight = com.aldebaran.qi.sdk.`object`.geometry.Vector3(-1.0,0.5,0.0)
    //say variables
    lateinit var qiContext: QiContext
    private var sayFuture:Future<Void>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        QiSDK.register(this, this)
        Log.i(TAG, "onCreate called")

        //create the scan hub
        hub = Hub.getInstance()

        //get the right bluetooth permission
                requestPermissions(
                    arrayOf(
                        ACCESS_COARSE_LOCATION
                    ), 1
                )
        //hub init
        if (!hub.init(this)) {
            Log.e(TAG, "Could not initialize the Hub.")
            finish()
        }
        else {
            // Disable standard Myo locking policy. All poses will be delivered.
            hub.setLockingPolicy(Hub.LockingPolicy.NONE)
            // scan Myo by activity
            Log.i(TAG, "start connect activity")
            startConnectActivity()
            // Create listener for Myo
            listener = get_listener()
            Log.i(TAG, "listener added")
        }

        //button to reconnect Myo if disconnected
        connectButton.setOnClickListener {

            if (!hub.init(this)) {
                Log.e(TAG, "Could not initialize the Hub.")
                finish()
            }
            else {
                // Disable standard Myo locking policy. All poses will be delivered.
                hub.setLockingPolicy(Hub.LockingPolicy.NONE)
                // scan Myo by activity
                Log.i(TAG, "start connect activity")
                startConnectActivity()
                // Create listener for Myo
                listener = get_listener()
                Log.i(TAG, "listener added")
            }
        }

        //button to enable/disable Pepper control
        controlButton.setOnClickListener {
            controlEnabled=!controlEnabled
            if(controlEnabled){
                controlButton.setText("Control: on")
            } else {
                controlButton.setText("Control: off")
            }
        }

    }

    override fun onResume() {
        super.onResume()
        hub.addListener(listener)
    }

    override fun onPause() {
        super.onPause()
        hub.removeListener(listener)
    }

    override fun onDestroy() {
        hub.shutdown()
        QiSDK.unregister(this, this)
        Log.i(TAG, "onDestroy called")
        super.onDestroy()
    }

    override fun onRobotFocusGained(qiContext: QiContext) {
        Log.i(TAG,"onRobotFocusGained called")
        this.qiContext = qiContext
        //motion initialisation
        // Get the Actuation service from the QiContext.
        actuation = qiContext.actuation
        // Get the robot frame.
        robotFrame = actuation.robotFrame()
        // Get the Mapping service from the QiContext.
        mapping = qiContext.mapping
        // Create a FreeFrame with the Mapping service.
        targetFrame = mapping.makeFreeFrame()

        // Create a GoTo action.
        goTo = GoToBuilder.with(qiContext) // Create the builder with the QiContext.
            .withFrame(targetFrame.frame()) // Set the target frame.
            .build() // Build the GoTo action.
        // Add an on started listener on the GoTo action.
        goTo.addOnStartedListener { Log.i(TAG, "GoTo action started.") }

        // Create a LookAt action.
        lookAt = LookAtBuilder.with(qiContext) // Create the builder with the context.
            .withFrame(targetFrame.frame()) // Set the target frame.
            .build() // Build the LookAt action.
        // Set the LookAt policy to look with the whole body.
        lookAt.setPolicy(LookAtMovementPolicy.HEAD_AND_BASE)
        // Add an on started listener on the LookAt action.
        lookAt.addOnStartedListener { Log.i(TAG, "LookAt action started.") }

    }

    override fun onRobotFocusRefused(reason: String?) {}

    override fun onRobotFocusLost() {
        // Remove on started listeners from the GoTo action.
        if (goTo != null) {
            goTo.removeAllOnStartedListeners()
        }
        // Remove on started listeners from the LookAt action.
        if (lookAt != null) {
            lookAt.removeAllOnStartedListeners()
        }
    }

    private fun startConnectActivity() {
        /**
         * Init activity to connect Myo device
         */
        startActivity(Intent(this@MainActivity, ScanActivity::class.java))
    }

    private fun get_listener(): AbstractDeviceListener {
        /**
         * Get the listener to myo device
         */
        return object : AbstractDeviceListener() {

            override fun onConnect(myo: Myo?, timestamp: Long) {
                /**
                 * When myo is connected, show it in a label
                 */
                Log.i(TAG, "Myo connected")
                textView.setText("Myo Connected!")
                textView.setTextColor(
                    ContextCompat.getColor(
                        this@MainActivity,
                        android.R.color.holo_blue_dark
                    )
                )
            }

            override fun onDisconnect(myo: Myo?, timestamp: Long) {
                /**
                 * When myo is disconnected, show it in a label
                 */
                Log.i(TAG, "Myo disconnected")
                textView.setText("Myo Disconnected!")
                textView.setTextColor(
                    ContextCompat.getColor(
                        this@MainActivity,
                        android.R.color.holo_red_dark
                    )
                )
            }

            override fun onPose(myo: Myo?, timestamp: Long, pose: Pose?) {
                /**
                 * This interface in called when Myo detect some pose
                 */
                textView.setText("Pose: " + pose!!.toString())

                if (pose == Pose.REST) {
                    Log.i(TAG,"rest")
                    lookAtFuture?.cancel(false)
                    goToFuture?.requestCancellation()
                    sayFuture?.requestCancellation()

                } else if (pose == Pose.DOUBLE_TAP) {
                    Log.i(TAG,"double tap")
                    controlEnabled=!controlEnabled
                    if(controlEnabled){
                        controlButton.setText("Control: on")
                        SayBuilder.with(qiContext).withText("Control on.").buildAsync()
                            .andThenCompose {say ->
                                sayFuture = say.async().run()
                                sayFuture
                            }
                    } else {
                        controlButton.setText("Control: off")
                        SayBuilder.with(qiContext).withText("Control off.").buildAsync()
                            .andThenCompose {say ->
                                sayFuture = say.async().run()
                                sayFuture
                            }
                    }

                } else if (pose == Pose.WAVE_IN) {
                    //make Pepper look at the direction pointed
                    Log.i(TAG,"wave in")
                    if(controlEnabled) {
                        if(myo!!.arm == Arm.RIGHT) {
                            transform = TransformBuilder.create()
                                .fromTranslation(lookLeft)
                        } else {
                            transform = TransformBuilder.create()
                                .fromTranslation(lookRight)
                        }
                        //update target frame
                        targetFrame.async().update(robotFrame, transform, 0L)
                            .thenCompose{
                                lookAtFuture = lookAt.async().run()
                                lookAtFuture
                            }
                            .thenConsume { future ->
                            if (future.isSuccess) {
                                Log.i(TAG, "LookAt action finished with success.")
                            } else if (future.isCancelled) {
                                Log.i(TAG, "LookAt action was cancelled.")
                            } else {
                                Log.e(TAG, "LookAt action finished with error.", future.error)
                            }
                        }
                    }

                } else if (pose == Pose.WAVE_OUT) {
                    //make Pepper look at the direction pointed
                    Log.i(TAG,"wave out")
                    if(controlEnabled) {
                        if (myo!!.arm == Arm.RIGHT) {
                            transform = TransformBuilder.create()
                                .fromTranslation(lookRight)
                        } else {
                            transform = TransformBuilder.create()
                                .fromTranslation(lookLeft)
                        }
                        //update target frame
                        targetFrame.async().update(robotFrame, transform, 0L)
                            .thenCompose {
                                lookAtFuture = lookAt.async().run()
                                lookAtFuture
                            }
                            .thenConsume { future ->
                            if (future.isSuccess) {
                                Log.i(TAG, "LookAt action finished with success.")
                            } else if (future.isCancelled) {
                                Log.i(TAG, "LookAt action was cancelled.")
                            } else {
                                Log.e(TAG, "LookAt action finished with error.", future.error)
                            }
                        }
                    }

                } else if (pose == Pose.FIST) {
                    Log.i(TAG,"fist")
                    SayBuilder.with(qiContext).withText("Hey!").buildAsync()
                        .andThenCompose {say ->
                            sayFuture = say.async().run()
                            sayFuture
                        }

                } else if (pose == Pose.FINGERS_SPREAD) {
                    //move forward 1 meter when fingers spread
                    Log.i(TAG, "fingers spread")
                    if(controlEnabled) {
                        // Create a transform corresponding to a 1 meter forward translation.
                        transform = TransformBuilder.create()
                            .fromXTranslation(10.0)
                        // Update the target location relatively to Pepper's current location.
                        targetFrame.async().update(robotFrame, transform, 0L)
                            .thenCompose {
                                goToFuture = goTo.async().run()
                                goToFuture
                            }
                            .thenConsume { future ->
                                if (future.isSuccess) {
                                    Log.i(TAG, "GoTo action finished with success.")
                                } else if (future.hasError()) {
                                    Log.e(TAG, "GoTo action finished with error.", future.error)
                                }
                            }
                    }
                }
            }
/*
// Execute the GoTo action asynchronously.
                    val goToFuture =
                    // Add a lambda to the action execution.
                    goToFuture
 */
            override fun onArmSync(myo: Myo?, timestamp: Long, arm: Arm?, xDirection: XDirection?) {
                /**
                 * Show in the label, the arm where myo is wearing
                 */
                textView.setText(if (myo!!.arm == Arm.LEFT) "Arm left" else "Arm right")
            }

            override fun onArmUnsync(myo: Myo?, timestamp: Long) {
                /**
                 * Util to detect if myo is disconnected
                 */
                textView.setText("Arm not Detected")
            }

        }
    }

}
