package xyz.gianlu.librespot.audio.cdn;

import com.spotify.metadata.Metadata;
import com.spotify.storage.StorageResolve.StorageResolveResponse;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.gianlu.librespot.audio.HaltListener;
import xyz.gianlu.librespot.audio.NormalizationData;
import xyz.gianlu.librespot.audio.PlayableContentFeeder;
import xyz.gianlu.librespot.audio.PlayableContentFeeder.LoadedStream;
import xyz.gianlu.librespot.common.Utils;
import xyz.gianlu.librespot.core.Session;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Gianlu
 */
public final class CdnFeedHelper {
    private static final Logger LOGGER = LogManager.getLogger(CdnFeedHelper.class);

    private CdnFeedHelper() {
    }

    @NotNull
    private static HttpUrl getUrl(@NotNull Session session, @NotNull StorageResolveResponse resp) {
        return HttpUrl.get(resp.getCdnurl(session.random().nextInt(resp.getCdnurlCount())));
    }

    public static @NotNull LoadedStream loadTrack(@NotNull Session session, Metadata.@NotNull Track track, Metadata.@NotNull AudioFile file,
                                                  @NotNull HttpUrl url, boolean preload, @Nullable HaltListener haltListener) throws IOException, CdnManager.CdnException {
        long start = System.currentTimeMillis();
        byte[] key = session.audioKey().getAudioKey(track.getGid(), file.getFileId());
        int audioKeyTime = (int) (System.currentTimeMillis() - start);

        CdnManager.Streamer streamer = session.cdn().streamFile(file, key, url, haltListener);
        InputStream in = streamer.stream();
        NormalizationData normalizationData = NormalizationData.read(in);
        if (in.skip(0xa7) != 0xa7) throw new IOException("Couldn't skip 0xa7 bytes!");
        return new LoadedStream(track, streamer, normalizationData, new PlayableContentFeeder.Metrics(file.getFileId(), preload, preload ? -1 : audioKeyTime));
    }

    public static @NotNull LoadedStream loadTrack(@NotNull Session session, Metadata.@NotNull Track track, Metadata.@NotNull AudioFile file,
                                                  @NotNull StorageResolveResponse storage, boolean preload, @Nullable HaltListener haltListener) throws IOException, CdnManager.CdnException {
        return loadTrack(session, track, file, getUrl(session, storage), preload, haltListener);
    }

    public static @NotNull LoadedStream loadEpisodeExternal(@NotNull Session session, Metadata.@NotNull Episode episode, @Nullable HaltListener haltListener) throws IOException, CdnManager.CdnException {
        try (Response resp = session.client().newCall(new Request.Builder().head()
                .url(episode.getExternalUrl()).build()).execute()) {

            if (resp.code() != 200)
                LOGGER.warn("Couldn't resolve redirect!");

            HttpUrl url = resp.request().url();
            LOGGER.debug("Fetched external url for {}: {}", Utils.bytesToHex(episode.getGid()), url);

            CdnManager.Streamer streamer = session.cdn().streamExternalEpisode(episode, url, haltListener);
            return new LoadedStream(episode, streamer, null, new PlayableContentFeeder.Metrics(null, false, -1));
        }
    }

    public static @NotNull LoadedStream loadEpisode(@NotNull Session session, Metadata.@NotNull Episode episode, @NotNull Metadata.AudioFile file, @NotNull HttpUrl url, @Nullable HaltListener haltListener) throws IOException, CdnManager.CdnException {
        long start = System.currentTimeMillis();
        byte[] key = session.audioKey().getAudioKey(episode.getGid(), file.getFileId());
        int audioKeyTime = (int) (System.currentTimeMillis() - start);

        CdnManager.Streamer streamer = session.cdn().streamFile(file, key, url, haltListener);
        InputStream in = streamer.stream();
        NormalizationData normalizationData = NormalizationData.read(in);
        if (in.skip(0xa7) != 0xa7) throw new IOException("Couldn't skip 0xa7 bytes!");
        return new LoadedStream(episode, streamer, normalizationData, new PlayableContentFeeder.Metrics(file.getFileId(), false, audioKeyTime));
    }

    public static @NotNull LoadedStream loadEpisode(@NotNull Session session, Metadata.@NotNull Episode episode, @NotNull Metadata.AudioFile file, @NotNull StorageResolveResponse storage, @Nullable HaltListener haltListener) throws IOException, CdnManager.CdnException {
        return loadEpisode(session, episode, file, getUrl(session, storage), haltListener);
    }
}
