# TwitterToMastodonBot
This bot can read tweets from specified public accounts and repost them to a single Mastodon account. Requires Twitter dev credentials and a Mastodon sign in. Mostly based on the official Twitter Java SDK and the Scaladon (https://github.com/Sciss/scaladon) Mastodon library. The bot does not currently replace Twitter tracking links or copy over images, and it filters out retweets, quote tweets, and replies.

## Setup
This bot is written in Scala, using SBT and meant to run with OpenJDK 11. You will need to install both of those to use this bot. After that, clone the repository with:
```
git clone git@github.com:bwebster4/TwitterToMastodonBot.git
```

You will need to setup a Twitter Developer account (the lowest level of verification is fine) and a Mastodon account to use this bot. Once they are setup, create an `application.conf` file by copying the `src/main/resources/reference.conf` file, and place it in the same directory. Fill out the required fields in that file, and replace default followed accounts.

## Usage
To build this bot for testing, simply run the following command in the top directory of this repository. Make sure you have created an application.conf file.
```
sbt run
```

For deploying this bot on a remote machine, you can run the following command to build a Fat Jar file.
```
sbt assembly
```
To execute the Fat Jar:
```
java -jar <your filename>.jar
```
I would recommend setting up a systemd/systemctl service to easily manage running the jar file.

## Examples
If you want to see what this bot looks like in the wild, it is being used to manage the @mbta_birdbot@mastodonapp.boston account.
