# Basic Krypton Setup
This is just a bare-bones setup for Krypton, if you're short on time and just want to build the app and use it as a Notepad with a Gemini Wrapper :(

This README is supposed to provide a quick 30-second setup, with the Gemini-API.

> [!IMPORTANT]
> In this basic setup, you will not have majority of the features of the app!!!
> You can perform CRUD on files, use basic chat, and use all the agents. RAG & Web Search will NOT be available here.

## Prerequisites


|          | Item               | Description                                        | Link                                                             |
| -------- | ------------------ | -------------------------------------------------- | ---------------------------------------------------------------- |
|          |                    |                                                    |                                                                  |
| Required | **JDK17+**         |                                                    |                                                                  |
| Required | **Android SDK**    | An Android SDK is required to be installed locally |                                                                  |
| Optional | **Gemini API Key** | For Chat using Gemini                              | [Get a Gemini API Key](https://aistudio.google.com/app/api-keys) |


----
## How to Run the App
Here is a step-by-step guide for running and testing out the app.

### **Step 1: Clone the Git Repository**
You need to clone the Git Repository into a clean location.

```git
git clone https://github.com/JohnLocke117/Krypton.git
cd Krypton
```

> [!important]
> It is highly recommended to open the project in IntelliJ Idea as it will provide a seamless setup process, else you'll have to perform many steps manually ;0


### **Step 2: Setup Environment Variables**
Now, we're going to wire up everything in a `local.properties` file.

Open up your project in IntelliJ. Gradle Sync will start automatically, and it will create a `local.properties` file at the project root.

Download the Android-SDK here from the IntelliJ menu (Settings -> Android SDK Manager).
Once you have downloaded the Android-SDK, Gradle will automatically include the `sdk.dir` in the `local.properties`.


Then, in `local.properties`, below `sdk.dir`, add your Environment Variables as below:

```txt
sdk.dir=<PATH_TO_ANDROID_SDK>

GEMINI_API_BASE_URL=https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent
GEMINI_API_KEY=<YOUR_GEMINI_API_KEY>
```

and you're good to go!


### **Step 3: Run the Desktop App**
After all is in place, you can build and run the app via gradle.
Open another terminal and navigate to the project root. There, run:

```sh
# macOS / Linux
./gradlew :composeApp:run

# Windows
.\gradlew.bat :composeApp:run
```

On the first build, Gradle will download all the dependencies hence might take some time to boot up. Further runs will be quick.

### **Step 4: Run the Android App**
From IntelliJ Idea (recommended) or Android Studio:
1. Open the `krypton` project in IntelliJ
2. Wait for Gradle sync to finish.
3. Select the `android` run configuration.
4. Choose a physical device/emulator (virtual device).
5. Click Run.

Refer to [Running Android Apps in IntelliJ](https://kotlinlang.org/docs/multiplatform/multiplatform-create-first-app.html#run-your-application) for more details if needed on how to setup and run on Android.

Optionally, you can view the `LogCat` logs for monitoring the app.