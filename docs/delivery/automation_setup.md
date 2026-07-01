# Play Store Automation Setup

We have configured two ways to automate your Play Store deployments: **Triple-T Gradle Play Publisher** and **Fastlane**.

## 1. Prerequisites (Mandatory)
Both tools require a Google Play Service Account JSON key.
1. Go to the [Google Cloud Console](https://console.cloud.google.com/).
2. Create a Service Account with **Editor** permissions for your project.
3. Generate a **JSON Key** and download it.
4. Rename it to `service-account.json` and place it in the **root** of this project.
5. **DO NOT commit this file to Git.** It is added to `.gitignore`.

## 2. Triple-T Gradle Play Publisher (Recommended)
This is integrated directly into Gradle.

### Useful Commands:
- **Sync Metadata**: `./gradlew :mobile:publishListing`
- **Upload Internal Build**: `./gradlew :mobile:publishBundle`
- **Sync Everything (All modules)**: `./gradlew publish`

The metadata is expected in: `mobile/src/main/play/`

## 3. Fastlane (Supply)
Requires Ruby and Fastlane installed (`gem install fastlane`).

### Useful Commands:
- **Sync Metadata**: `fastlane sync_metadata`
- **Deploy Internal**: `fastlane deploy_internal`

The metadata is located in: `fastlane/metadata/android/`

## Metadata Structure
We have created the directory structure for 15 languages in `fastlane/metadata/android/`. 
To update a description, edit the `.txt` files in the corresponding language folder:
- `title.txt`: Max 50 chars
- `short_description.txt`: Max 80 chars
- `full_description.txt`: Max 4000 chars
