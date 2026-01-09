# Complete Krypton Setup
This README details the complete step-by-step process of running Krypton at its best, with all its features. The complete setup takes around ~10 minutes (depending on your Internet) and is the recommedned way of checking out Krypton's capabilities.


----
## Prerequisites
Here are the prerequisites for running the app. See below for step-by-step setup.


|          | Item                       | Description                                                                 | Link                                                             |
| -------- | -------------------------- | --------------------------------------------------------------------------- | ---------------------------------------------------------------- |
|          |                            |                                                                             |                                                                  |
| Required | **JDK17+**                 |                                                                             |                                                                  |
| Required | **Android SDK**            | An Android SDK is required to be installed locally                          |                                                                  |
| Optional | **Ollama**                 | For Chat using Ollama (Local)                                               | [Download Ollama](https://ollama.com/download)                   |
| Optional | **Gemini API Key**         | For Chat using Gemini                                                       | [Get a Gemini API Key](https://aistudio.google.com/app/api-keys) |
| Optional | **Tavilly API Key**        | To enable Web Search in Chat Responses                                      | [Get a Tavilly API Key](https://app.tavily.com/home)             |
| Optional | **ChromaDB**               | For RAG features. Can be run locally                                        |                                                                  |
| Optional | **ChromaDB Cloud API Key** | For RAG features. ChromaDB Cloud can also be used instead of local ChromaDB | [ChromaDB Cloud](https://www.trychroma.com/)                     |


Note that either Ollama or a GeminiAPI Key are *required* for chat to function. You can toggle which one you want to use in the UI or from `composeApp/settings.json`.
I have included a default `settings.json` as well with this app to make the setup easier. That'll be different if this app is published.

You can use Ollama OR Gemini for the Chat; ChromaDB OR ChromaDB Cloud for RAG (you only need either of these to use the Chat and RAG features respectively). 


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


### **Step 2: Start up the External Services**
For this complete run, we'll setup all the services. You can skip some if you're feeling like it ;)

#### **Setup Ollama**
(*This is Optional if you just want to use GeminiAPI. You can skip this part.*)

Here, we'll setup Ollama for local LLM responses in the chat. We'll be needing a Generator model, an Embedding Model, and a (optional) Re-Ranker Model.
You can download the Ollama client from [Ollama Client Download](https://ollama.com/download).


```sh
# Check if Ollama is installed
ollama --version

# Install a Generator Model
ollama pull llama3.1:8b

# Install an Embedding Model
ollama pull mxbai-embed-large:335m

# (Optional) Install a Dedicated Re-Ranker Model
ollama pull dengcao/Qwen3-Reranker-8B:Q3_K_M

# List all Installed Models:
ollama list
```

After downloading all the models that you need, just start up the server in another terminal:

```sh
ollama serve
```

Krypton assumes that Ollama will run by default on `http://localhost:11434`.
It is recommended to use the above-mentioned models for a quick run-through. If you want to use other models, then you can change in `composeApp/settings.json` or directly from the app settings UI.

#### **Get Tavilly API Key**
(*Tavilly is used for enabling Web Search in the Chat. You CAN SKIP this part if you don't require web searches*)

Go to [Tavilly](https://app.tavily.com/home), create your free account, and copy the API Key.


#### **Setup ChromaDB**
(*ChromaDB is required for enabling RAG in the chat. You can skip this part if you just want to chat normally.*)

ChromaDB is the VectorDB that we'll be using for RAG here. It is required if you want to query your notes, but not required for just normal chats.

ChromaDB can be installed via the ChromaDB CLI or via a more recommended way that is Docker.
To run ChromaDB via Docker, run the following commands in a terminal:

```sh
# Pull the image
docker pull chromadb/chroma:latest

# Create a Volume
docker volume create chroma-data

# Run the container, mapping port 8000 and using chroma-data Volume for persistance
docker run -d \
  --name chromadb \
  -p 8000:8000 \
  --mount type=volume,src=chroma-data,target=/data \
  -e IS_PERSISTENT=TRUE \
  -e PERSIST_DIRECTORY=/data \
  -e ANONYMIZED_TELEMETRY=FALSE \
  chromadb/chroma:latest
```

OR

To install the ChromaDB via the CLI, refer here: [ChromaDB Local-CLI Setup](https://docs.trychroma.com/docs/overview/getting-started).


After the ChromaDB Server is installed, you can make sure that it is running properly on port 8000 via:

```sh
curl --location 'http://localhost:8000/api/v2/healthcheck'
```

OR

**Setup ChromaDB Cloud**

You can also setup a VectorDB in ChromaDB Cloud. Follow these steps:
- Go to [ChromaDB Cloud](https://www.trychroma.com/) and create a free account
- After that, create a Database named `defaultDB`
- Go to Database Settings, scroll down, and under the "Connect to your database" option, click on `.env`
- Generate an API Key there and copy all the values, and go to the next step.


### **Step 3: Setup Environment Variables**
Finally, we're going to wire up everything in a `local.properties` file.

Open up your project in IntelliJ. Gradle Sync will start automatically, and it will create a `local.properties` file at the project root.

Download the Android-SDK here from the IntelliJ menu (Settings -> Android SDK Manager).
Once you have downloaded the Android-SDK, Gradle will automatically include the `sdk.dir` in the `local.properties`.

At this stage, you must have:
- Setup Ollama OR have a GEMINI API Key
- Have a Tavilly API Key (If you want to use Web Search)
- Have Setup ChromaDB locally OR have created a Cloud ChromaDB Database


Then, in `local.properties`, below `sdk.dir`, add your Environment Variables as below:

```txt
sdk.dir=<PATH_TO_ANDROID_SDK>

GEMINI_API_BASE_URL=https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent
GEMINI_API_KEY=<YOUR_GEMINI_API_KEY>

TAVILLY_API_KEY=<YOUR_TAVILLY_API_KEY>

CHROMA_HOST=api.trychroma.com
CHROMA_API_KEY=<YOUR_CHROMADB_CLOUD_API_KEY>
CHROMA_TENANT=<YOUR_CHROMADB_CLOUD_TENANT_ID>
CHROMA_DATABASE=<YOUR_CHROMADB_CLOUD_DATABASE_NAME>
```

and you're good to go!


### **Step 4: Run the Desktop App**
By now, depending on what you've chosen you'll have:
1. Ollama Local running on Port 11434 OR GEMINI_API_KEY
2. ChromaDB Local running on Port 8000 OR ChromaDB Cloud

After all is in place, you can build and run the app via gradle.
Open another terminal and navigate to the project root. There, run:

```sh
# macOS / Linux
./gradlew :composeApp:run

# Windows
.\gradlew.bat :composeApp:run
```

On the first build, Gradle will download all the dependencies hence might take some time to boot up. Further runs will be quick.

### **Step 5: Run the Android App**
From IntelliJ Idea (recommended) or Android Studio:
1. Open the `krypton` project in IntelliJ
2. Wait for Gradle sync to finish.
3. Select the `android` run configuration.
4. Choose a physical device/emulator (virtual device).
5. Click Run.

Refer to [Running Android Apps in IntelliJ](https://kotlinlang.org/docs/multiplatform/multiplatform-create-first-app.html#run-your-application) for more details if needed on how to setup and run on Android.

Optionally, you can view the `LogCat` logs for monitoring the app.

