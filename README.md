# circomwitnesscalc

⚠️ This repository is currently WIP and experimental ⚠️

CircomWitnesscalc is an Android wrapper for the [iden3/circom-witnesscalc](https://github.com/iden3/circom-witnesscalc) library

## Platform Support

**Android**: Compatible with any Android device with 64 bit architecture.

## Requirements

- Android 7.0 (API level 24) or higher.

## Installation

Add 

```
implementation("io.iden3:circomwitnesscalc:0.0.1-alpha.1")
```

to the `build.gradle`

## Usage

#### calculateWitness

Function takes inputs json string and graph data file bytes and returns witness bytes.

```Kotlin
import io.iden3.circomwitnesscalc.*

// ...

val inputs: String = assets.open("inputs.json").bufferedReader().use { it.readText() }
val graphData: ByteArray = assets.open("authV2.wcd").loadIntoBytes()

val witness: ByteArray = calculateWitness(inputs, graphData)
```

## Example

To run the example project, clone the repo, and run `app` module application.

## License

circom-witnesscalc-android is part of the iden3 project and licensed under MIT and APACHE 2.0 licences. Please check the [LICENSE-MIT](./LICENSE-MIT.txt) and [LICENSE-APACHE](./LICENSE-APACHE.txt) files for more details.
