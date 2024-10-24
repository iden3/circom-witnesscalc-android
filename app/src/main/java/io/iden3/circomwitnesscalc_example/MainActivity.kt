package io.iden3.circomwitnesscalc_example

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import io.iden3.circomwitnesscalc.WitnesscalcError
import io.iden3.circomwitnesscalc.calculateWitness
import io.iden3.circomwitnesscalc_example.ui.theme.circomwitnesscalc_exampleTheme
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader


class MainActivity : ComponentActivity() {
    private var inputsUri = mutableStateOf<String?>(null)
    private var inputs: String? = null
    private var graphDataUri = mutableStateOf<String?>(null)
    private var graphData: ByteArray? = null

    private var witness: ByteArray? = null
    private var timestamp = mutableLongStateOf(0L)
    private val hasWitness = mutableStateOf(false)
    private var errorMessage = mutableStateOf("")


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            circomwitnesscalc_exampleTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Example(
                        onInputsSelected = {
                            val documentFile = DocumentFile.fromSingleUri(baseContext, it)
                            inputsUri.value = documentFile?.name

                            inputs = contentResolver.openInputStream(it)?.readContents()
                        },
                        resetInputs = {
                            inputsUri.value = null
                            inputs = null
                        },
                        inputsUri = inputsUri,
                        onGraphDataSelected = {
                            val documentFile = DocumentFile.fromSingleUri(baseContext, it)
                            inputsUri.value = documentFile?.name

                            graphData = contentResolver.openInputStream(it)?.loadIntoBytes()
                        },
                        resetGraphData = {
                            graphDataUri.value = null
                            graphData = null
                        },
                        graphDataUri = graphDataUri,
                        hasWitness = hasWitness,
                        timestamp = timestamp,
                        error = errorMessage,
                        onGenerate = { calcWitness() },
                        onShare = { shareWitness() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun calcWitness() {
        val start = System.currentTimeMillis()

        val inputs = if (this.inputs != null) {
            this.inputs!!
        } else {
            assets.open("authV2_inputs.json").bufferedReader().use { it.readText() }
        }
        val graphData = if (this.graphData != null) {
            graphData!!
        } else {
            assets.open("authV2.wcd").loadIntoBytes()
        }

        try {
            witness = calculateWitness(inputs, graphData)
            hasWitness.value = true
        } catch (error: WitnesscalcError) {
            errorMessage.value = error.message!!
            hasWitness.value = false
        }

        timestamp.longValue = System.currentTimeMillis() - start
    }

    private fun shareWitness() {
        val wtns = witness ?: return

        val witnessesDir = File(cacheDir, "witnesses")
        if (!witnessesDir.exists()) witnessesDir.mkdir()

        val witnessFile = File(witnessesDir, "witness.wtns")
        if (witnessFile.exists()) witnessFile.delete()
        witnessFile.createNewFile()
        witnessFile.writeBytes(wtns)

        val contentUri = FileProvider.getUriForFile(
            baseContext,
            "io.iden3.circomwitnesscalc_example.provider",
            witnessFile
        )

        val intentShareFile = Intent(Intent.ACTION_SEND)

        intentShareFile.setType("application/octet-stream")
        intentShareFile.putExtra(Intent.EXTRA_STREAM, contentUri)

        intentShareFile.putExtra(Intent.EXTRA_SUBJECT, "Save witness")
        intentShareFile.putExtra(Intent.EXTRA_TEXT, "Save witness")

        startActivity(Intent.createChooser(intentShareFile, "Save witness"))
    }
}


private fun InputStream.loadIntoBytes(): ByteArray {
    val buf = ByteArray(available())
    read(buf)
    close()
    return buf
}

private fun InputStream.readContents(): String {
    val inputStreamReader = InputStreamReader(this)
    val bufferedReader = BufferedReader(inputStreamReader)
    val sb = StringBuilder()
    var s: String?
    while ((bufferedReader.readLine().also { s = it }) != null) {
        sb.append(s)
    }
    close()
    return sb.toString()
}

@Composable
fun Example(
    onInputsSelected: (Uri) -> Unit,
    resetInputs: () -> Unit,
    inputsUri: MutableState<String?>,
    onGraphDataSelected: (Uri) -> Unit,
    resetGraphData: () -> Unit,
    graphDataUri: MutableState<String?>,
    hasWitness: MutableState<Boolean>,
    timestamp: MutableLongState,
    error: MutableState<String>,
    onGenerate: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasCustomInputs = inputsUri.value != null
    val hasCustomGraphData = graphDataUri.value != null

    val inputsPicker = rememberLauncherForActivityResult(
        contract = GetCustomContents(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                onInputsSelected(uris.first())
            }
        })

    val graphDataPicker = rememberLauncherForActivityResult(
        contract = GetCustomContents(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                onGraphDataSelected(uris.first())
            }
        })

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .then(modifier)
    ) {
        Text(if (hasCustomInputs) "Custom inputs from ${inputsUri.value}" else "Default authV2 inputs selected")
        Row {
            Button(onClick = { inputsPicker.launch("application/json") }) {
                Text("Select inputs")
            }
            Button(onClick = resetInputs, enabled = inputsUri.value != null) {
                Text("Reset inputs")
            }
        }
        Text(if (hasCustomGraphData) "Custom graph data from ${graphDataUri.value}" else "Default authV2 graph data selected")
        Row {
            Button(onClick = { graphDataPicker.launch("application/octet-stream") }) {
                Text("Select graph data")
            }
            Button(onClick = resetGraphData, enabled = graphDataUri.value != null) {
                Text("Reset graph data")
            }
        }
        Text(
            if (error.value.isNotBlank()) "Error: " + error.value
            else if (hasWitness.value) ("Witness generated in ${timestamp.longValue} millis")
            else "Generate witness",
        )
        Button(onClick = onGenerate) {
            Text("Generate")
        }
        if (hasWitness.value)
            Button(onClick = onShare) {
                Text("Share")
            }
    }
}


class GetCustomContents(
    private val isMultiple: Boolean = false, //This input check if the select file option is multiple or not
) : ActivityResultContract<String, List<@JvmSuppressWildcards Uri>>() {

    override fun createIntent(context: Context, input: String): Intent {
        return Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = input //The input option es the MIME Type that you need to use
            putExtra(Intent.EXTRA_LOCAL_ONLY, true) //Return data on the local device
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, isMultiple) //If select one or more files
                .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
        return intent.takeIf {
            resultCode == Activity.RESULT_OK
        }?.getClipDataUris() ?: emptyList()
    }

    internal companion object {

        //Collect all Uris from files selected
        internal fun Intent.getClipDataUris(): List<Uri> {
            // Use a LinkedHashSet to maintain any ordering that may be
            // present in the ClipData
            val resultSet = LinkedHashSet<Uri>()
            data?.let { data ->
                resultSet.add(data)
            }
            val clipData = clipData
            if (clipData == null && resultSet.isEmpty()) {
                return emptyList()
            } else if (clipData != null) {
                for (i in 0 until clipData.itemCount) {
                    val uri = clipData.getItemAt(i).uri
                    if (uri != null) {
                        resultSet.add(uri)
                    }
                }
            }
            return ArrayList(resultSet)
        }
    }
}

@SuppressLint("UnrememberedMutableState")
@Preview(showBackground = true)
@Composable
fun ExamplePreview() {
    circomwitnesscalc_exampleTheme {
        Example(
            onInputsSelected = {},
            resetInputs = {},
            inputsUri = mutableStateOf("file:///Users/user/dev/projects/cross-chain-verification-example/circuits/credentialAtomicQueryMTPV2OnChain/authV2.wcd"),
            onGraphDataSelected = {},
            resetGraphData = {},
            graphDataUri = mutableStateOf(null),
            hasWitness = mutableStateOf(false),
            timestamp = mutableLongStateOf(697),
            error = mutableStateOf(""),
            onGenerate = {},
            onShare = {}
        )
    }
}
