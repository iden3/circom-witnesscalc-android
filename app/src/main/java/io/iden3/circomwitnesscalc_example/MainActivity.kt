package io.iden3.circomwitnesscalc_example

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import io.iden3.circomwitnesscalc.WitnesscalcError
import io.iden3.circomwitnesscalc.calculateWitness
import io.iden3.circomwitnesscalc_example.ui.theme.circomwitnesscalc_exampleTheme
import io.iden3.rapidsnark.groth16Prove
import io.iden3.rapidsnark.groth16ProveWithZKeyFilePath
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader

var executionTime = 0

class MainActivity : ComponentActivity() {
    private var inputsUri = mutableStateOf<String?>(null)
    private var inputs: String? = null
    private var graphDataUri = mutableStateOf<String?>(null)
    private var graphData: ByteArray? = null
    private var zkeyUri = mutableStateOf<Uri?>(null)
    private var zkey: ByteArray? = null

    private var witness: ByteArray? = null
    private var timestamp = mutableLongStateOf(0L)
    private val hasWitness = mutableStateOf(false)
    private var errorMessage = mutableStateOf("")

    private val proof = mutableStateOf("")


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
                            graphDataUri.value = documentFile?.name

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
                        modifier = Modifier.padding(innerPadding),
                        onZkeySelected = {
                            val documentFile = DocumentFile.fromSingleUri(baseContext, it)
                            zkeyUri.value = documentFile?.uri

                            copyZkeyToCache(it)
                        },
                        resetZkey = {
                            zkeyUri.value = null
                            zkey = null
                        },
                        zkeyUri = zkeyUri,
                        generateProof = {
                            generateProof()
                        },
                        proof = proof,
                    )
                }
            }
        }
    }

    private fun copyZkeyToCache(zkeyUri: Uri) {
        cacheDir.mkdir()

        val zkeyFile = File(cacheDir, zkeyUri.pathSegments.last().split('/').last())
        if (zkeyFile.exists()) return
        zkeyFile.createNewFile()

        contentResolver.openInputStream(zkeyUri)?.use { input ->
            zkeyFile.outputStream().use { output ->
                input.copyTo(output)
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

    private fun generateProof() {
        val witness = this.witness!!

        val zkeyPath = if (zkeyUri.value == null) {
            val authV2File = File(cacheDir, "authV2.zkey")
            if (!authV2File.exists()) {
                val authV2InputStream = assets.open("authV2.zkey")
                val authV2OutputStream = authV2File.outputStream()
                authV2InputStream.copyTo(authV2OutputStream)
            }
            authV2File.path
        } else {
            val fileName = zkeyUri.value!!.pathSegments.last().split('/').last()
            File(cacheDir, fileName).path
        }

        val executionStart = System.currentTimeMillis()

        val proof = groth16ProveWithZKeyFilePath(
            zkeyPath = zkeyPath,
            witness = witness
        )

        executionTime = (System.currentTimeMillis() - executionStart).toInt()

        this.proof.value =
            "Proof calculated in $executionTime ms\n" + proof.proof + "\n" + proof.publicSignals
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
    onZkeySelected: (Uri) -> Unit,
    resetZkey: () -> Unit,
    zkeyUri: MutableState<Uri?>,
    generateProof: () -> Unit,
    proof: MutableState<String>,
    modifier: Modifier = Modifier
) {
    val scrollState = ScrollState(0)

    val hasCustomZkey = zkeyUri.value != null
    val hasCustomInputs = inputsUri.value != null
    val hasCustomGraphData = graphDataUri.value != null

    val zkeyPicker = rememberLauncherForActivityResult(
        contract = GetCustomContents(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                onZkeySelected(uris.first())
            }
        })

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
            .padding(16.dp)
            .verticalScroll(scrollState)
            .then(modifier)
    ) {
        Text(if (hasCustomZkey) "Custom zkey from ${zkeyUri.value}" else "Default authV2 zkey selected")
        Row {
            Button(onClick = { zkeyPicker.launch("application/octet-stream") }) {
                Text("Select zkey")
            }
            Button(onClick = resetZkey, enabled = zkeyUri.value != null) {
                Text("Reset zkey")
            }
        }
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
        if (hasWitness.value)
            Button(onClick = generateProof) {
                Text("Generate proof")
            }
        if (proof.value.isNotBlank())
            SelectionContainer {
                Text("Proof: ${proof.value}")
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
            onShare = {},
            zkeyUri = mutableStateOf(Uri.EMPTY),
            onZkeySelected = {},
            resetZkey = {},
            generateProof = {},
            proof = mutableStateOf(""),
        )
    }
}
