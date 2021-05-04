package at.nirostar_labs.m4a_merge.service;

import at.nirostar_labs.m4a_merge.model.Chapter;
import at.nirostar_labs.m4a_merge.model.Track;
import javafx.beans.property.StringProperty;
import org.jcodec.common.*;
import org.jcodec.common.io.FileChannelWrapper;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Packet;
import org.jcodec.containers.mp4.Brand;
import org.jcodec.containers.mp4.boxes.MetaValue;
import org.jcodec.containers.mp4.demuxer.MP4Demuxer;
import org.jcodec.containers.mp4.muxer.MP4Muxer;
import org.jcodec.movtool.MetadataEditor;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class MP4Service {
    public void merge(String outputDir, Chapter chapter, List<Track> tracks, Map<ITunesMetaDataTag, StringProperty> m4aAlbumTags, String extension) throws IOException {
        String filename = String.format("%02d", chapter.getTrackNr()) + " " + chapter.getFilename().replaceAll("[\\\\/:*?\"<>|\t]", "_") + extension;
        String outputPath = outputDir + File.separator + filename;

        //System.out.println("New Chapter File " + outputPath);
        File outputFile = new File(outputPath);
        boolean ignore = outputFile.delete();
        ignore = outputFile.getParentFile().mkdirs();
        ignore = outputFile.createNewFile();
        mergeMP4Muxer(outputFile, chapter, tracks);
        addMeta(outputFile, chapter, m4aAlbumTags);
    }

    private void mergeMP4Muxer(File outputFile, Chapter chapter, List<Track> tracks) throws IOException {
        if (tracks.size() == 1) {
            Files.copy(tracks.get(0).getFile().toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            chapter.setProgress(0.9);
            tracks.get(0).setProgress(1);
            return;
        }
        DemuxerTrackMeta meta;
        try (MP4Demuxer mp4Demuxer = MP4Demuxer.createMP4Demuxer(new AutoFileChannelWrapper(tracks.get(0).getFile()))) {
            meta = mp4Demuxer.getAudioTracks().get(0).getMeta();
        }
        try (SeekableByteChannel outputChannelWrapper = new FileChannelWrapper(FileChannel.open(outputFile.toPath(), new StandardOpenOption[]{StandardOpenOption.WRITE}))) {
            MP4Muxer mp4Muxer = MP4Muxer.createMP4Muxer(outputChannelWrapper, Brand.MP4);
            //CodecMP4MuxerTrack muxerTrack = mp4Muxer.addCompressedAudioTrack(meta.getCodec(), meta.getAudioCodecMeta().getFormat());
            MuxerTrack muxerTrack = mp4Muxer.addAudioTrack(meta.getCodec(), meta.getAudioCodecMeta());
            int numberOfChapterFrames = getSumOfFrames(tracks);
            int chapterFrames = 0;
            for (Track track : tracks) {
                try (MP4Demuxer mp4Demuxer = MP4Demuxer.createMP4Demuxer(new AutoFileChannelWrapper(track.getFile()))) {
                    //System.out.println("Copying " + track.getFilename() + " ...");
                    int n = getFrames(track);
                    List<DemuxerTrack> audioTracks = mp4Demuxer.getAudioTracks();
                    for (DemuxerTrack demuxerTrack : audioTracks) {
                        if (demuxerTrack.getMeta().getCodec() != meta.getCodec() || !equal(demuxerTrack.getMeta().getAudioCodecMeta(), meta.getAudioCodecMeta())) {
                            throw new IllegalArgumentException("No tracks with different meta data allowed to merge.");
                        }
                        int i = 0;
                        Packet frame;
                        long nextUpdate = System.currentTimeMillis() + 100;
                        while ((frame = demuxerTrack.nextFrame()) != null) {
                            //System.out.println("Frame " + frame.getFrameNo() + ": " + frame.getData().toString());
                            muxerTrack.addFrame(frame);
                            i++;
                            chapterFrames++;
                            if (System.currentTimeMillis() >= nextUpdate) {
                                track.setProgress((double) i / n);
                                chapter.setProgress((double) chapterFrames / numberOfChapterFrames * 0.9);
                                nextUpdate = System.currentTimeMillis() + 100;
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                track.setProgress(1d);
                chapter.setProgress((double) chapterFrames / numberOfChapterFrames * 0.9);
            }
            mp4Muxer.finish();
        }
    }

    private void addMeta(File outputFile, Chapter chapter, Map<ITunesMetaDataTag, StringProperty> m4aAlbumTags) throws IOException {
        MetadataEditor metadataEditor = MetadataEditor.createFrom(outputFile);
        Map<Integer, MetaValue> itunesMeta = metadataEditor.getItunesMeta();
        Map<String, MetaValue> keyedMeta = metadataEditor.getKeyedMeta();
        itunesMeta.clear();
        for (Map.Entry<ITunesMetaDataTag, StringProperty> entry : m4aAlbumTags.entrySet()) {
            if (entry.getKey() == ITunesMetaDataTag.COVER) {
                MetaValue other = MetaValue.createOther(MetaValue.TYPE_JPEG, Base64.getDecoder().decode(entry.getValue().get()));
                itunesMeta.put(entry.getKey().getItunesKey(), other);
            } else {
                MetaValue string = MetaValue.createString(entry.getValue().get());
                itunesMeta.put(entry.getKey().getItunesKey(), string);
                //keyedMeta.put(entry.getKey().getID3Key(), string);
            }
        }
        itunesMeta.put(ITunesMetaDataTag.TRACK_NUMBER.getItunesKey(), createMetaValueForTrackNumber(chapter.getTrackNr(), 0));
        itunesMeta.put(ITunesMetaDataTag.TITLE.getItunesKey(), MetaValue.createString(chapter.getFilename()));
        metadataEditor.save(false);
        chapter.setProgress(1d);
    }

    private MetaValue createMetaValueForTrackNumber(int trackNr, int numberOfTracks) {
        byte[] trackNrBytes = toUnsignedByteArray(trackNr, 4);
        byte[] numberOfTracksBytes = toUnsignedByteArray(numberOfTracks, 4);
        byte[] result = new byte[8];

        System.arraycopy(trackNrBytes, 0, result, 0, 4);
        System.arraycopy(numberOfTracksBytes, 0, result, 4, 4);
        return MetaValue.createOther(0, result);
    }

    private byte[] toUnsignedByteArray(int number, int size) {
        byte[] bytes = new byte[size];
        for (int i = size - 1; i >= 0; i--) {
            bytes[i] = (byte) (number % 255);
            number /= 255;
        }
        return bytes;
    }

    private int getSumOfFrames(List<Track> tracks) {
        int n = 0;
        for (Track track : tracks) {
            n += getFrames(track);
        }
        return n;
    }

    private int getFrames(Track track) {
        int n = 0;
        try (AutoFileChannelWrapper input = new AutoFileChannelWrapper(track.getFile())) {
            MP4Demuxer mp4Demuxer = MP4Demuxer.createMP4Demuxer(input);
            List<DemuxerTrack> audioTracks = mp4Demuxer.getAudioTracks();
            for (DemuxerTrack audioTrack : audioTracks) {
                n += audioTrack.getMeta().getTotalFrames();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return n;
    }

    private boolean equal(AudioCodecMeta audioCodecMeta1, AudioCodecMeta audioCodecMeta2) {
        if (audioCodecMeta1 == audioCodecMeta2) return true;
        return audioCodecMeta1.getSampleSize() == audioCodecMeta2.getSampleSize() &&
                audioCodecMeta1.getChannelCount() == audioCodecMeta2.getChannelCount() &&
                audioCodecMeta1.getSampleRate() == audioCodecMeta2.getSampleRate() &&
                audioCodecMeta1.getSamplesPerPacket() == audioCodecMeta2.getSamplesPerPacket() &&
                audioCodecMeta1.getBytesPerPacket() == audioCodecMeta2.getBytesPerPacket() &&
                audioCodecMeta1.getBytesPerFrame() == audioCodecMeta2.getBytesPerFrame() &&
                audioCodecMeta1.isPCM() == audioCodecMeta2.isPCM() &&
                Objects.equals(audioCodecMeta1.getEndian(), audioCodecMeta2.getEndian()) &&
                Arrays.equals(audioCodecMeta1.getChannelLabels(), audioCodecMeta2.getChannelLabels());
    }
}
