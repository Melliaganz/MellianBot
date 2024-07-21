require('dotenv').config();
const { Client, GatewayIntentBits, ActivityType } = require('discord.js');
const { DisTube } = require('distube');
const { YtDlpPlugin } = require('@distube/yt-dlp');
const axios = require('axios');

const client = new Client({
  intents: [
    GatewayIntentBits.Guilds,
    GatewayIntentBits.GuildMessages,
    GatewayIntentBits.MessageContent,
    GatewayIntentBits.GuildVoiceStates,
  ],
});

const distube = new DisTube(client, {
  plugins: [new YtDlpPlugin()],
});

const prefix = process.env.PREFIX || '!';

client.once('ready', () => {
  console.log('Ready!');
  client.user.setActivity('des sons de fou', { type: ActivityType.Listening });
});

client.on('messageCreate', async message => {
  if (!message.content.startsWith(prefix) || message.author.bot) return;
  const args = message.content.slice(prefix.length).trim().split(/ +/);
  const command = args.shift().toLowerCase();

  if (command === 'play') {
    if (!message.member.voice.channel) {
      return message.channel.send('You need to be in a voice channel to play music!');
    }
    const query = args.join(' ');
    try {
      const videoUrl = await searchYouTube(query);
      await distube.play(message.member.voice.channel, videoUrl, {
        textChannel: message.channel,
        member: message.member,
      });
    } catch (err) {
      console.error(err);
      message.channel.send('An error occurred while trying to play the song.');
    }
  }

  if (command === 'stop') {
    if (!message.member.voice.channel) {
      return message.channel.send('You need to be in a voice channel to stop the music!');
    }
    distube.stop(message);
    message.channel.send('Stopped the music!');
  }

  if (command === 'skip') {
    try {
      distube.skip(message);
      message.channel.send('Skipped the song!');
    } catch (err) {
      console.error(err);
      message.channel.send('An error occurred while trying to skip the song.');
    }
  }

  if (command === 'queue') {
    const queue = distube.getQueue(message);
    if (!queue) {
      return message.channel.send('There is no queue!');
    }
    message.channel.send(`Current queue:\n${queue.songs.map((song, id) => `${id + 1}. ${song.name} - \`${song.formattedDuration}\``).join('\n')}`);
  }
});

// Function to search YouTube and get the first video URL
async function searchYouTube(query) {
  const url = `https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&q=${encodeURIComponent(query)}&key=${process.env.YOUTUBE_API_KEY}`;
  const response = await axios.get(url);
  if (response.data.items.length === 0) {
    throw new Error('No videos found');
  }
  const videoId = response.data.items[0].id.videoId;
  return `https://www.youtube.com/watch?v=${videoId}`;
}

// Event listeners for DisTube
distube
  .on('playSong', (queue, song) => {
    queue.textChannel.send(`Playing \`${song.name}\` - \`${song.formattedDuration}\`\nRequested by: ${song.user}`);
    client.user.setActivity(`Écoute ${song.name}`, { type: ActivityType.Listening });
  })
  .on('addSong', (queue, song) => queue.textChannel.send(`Added ${song.name} - \`${song.formattedDuration}\` to the queue by ${song.user}`))
  .on('addList', (queue, playlist) => queue.textChannel.send(`Added playlist \`${playlist.name}\` (${playlist.songs.length} songs) to the queue`))
  .on('finish', queue => {
    client.user.setActivity('des sons de fou', { type: ActivityType.Listening });
  })
  .on('stop', queue => {
    client.user.setActivity('des sons de fou', { type: ActivityType.Listening });
  })
  .on('error', (textChannel, e) => {
    console.error(e);
    textChannel.send(`An error encountered: ${e.message.slice(0, 2000)}`);
  });

client.login(process.env.DISCORD_TOKEN);
