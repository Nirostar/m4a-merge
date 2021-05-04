package at.nirostar_labs.m4a_merge.ui;


import at.nirostar_labs.m4a_merge.model.*;
import at.nirostar_labs.m4a_merge.service.ITunesMetaDataTag;
import at.nirostar_labs.m4a_merge.service.MP4Service;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.ProgressBarTreeTableCell;
import javafx.scene.control.cell.TextFieldTreeTableCell;
import javafx.scene.control.cell.TreeItemPropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.commons.text.StringSubstitutor;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.TagException;
import org.jcodec.containers.mp4.boxes.MetaValue;
import org.jcodec.movtool.MetadataEditor;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Controller implements Initializable {
    private static String PLAY_SYMBOL = "\u23F5";
    private static String PAUSE_SYMBOL = "\u23F8";
    private static String STOP_SYMBOL = "\u23F9";
    private static String NEXT_SYMBOL = "\u23EE";
    private static String PREV_SYMBOL = "\u23ED";
    private final Stage stage;
    private final MP4Service mp4Service;

    private final ObjectProperty<byte[]> imageBytes = new SimpleObjectProperty<>();

    @FXML
    private TextField workingDirectoryTextField;

    @FXML
    private TreeTableView<TableEntry> files;

    @FXML
    private Button addChapterButton;

    @FXML
    private MenuItem deleteChapterButton;

    @FXML
    private Button prevButton;

    @FXML
    private Button playButton;

    @FXML
    private Button nextButton;

    @FXML
    private AnchorPane playerPane;

    @FXML
    private Label fileNameLabel;

    @FXML
    private Slider timeSlider;

    @FXML
    private Label currentTimeLabel;

    @FXML
    private Label durationLabel;

    @FXML
    private TextField outputDirectoryTextField;

    @FXML
    private Button mergeButton;

    @FXML
    private Pane coverPane;

    @FXML
    private HBox coverHBox;

    @FXML
    private ImageView cover;

    @FXML
    private TextField bookTitleTextField;

    @FXML
    private TextField authorTextField;

    @FXML
    private TextField speakerTextField;

    @FXML
    private ComboBox<String> yearComboBox;

    @FXML
    private ProgressBar mergeProgressBar;

    @FXML
    private ChoiceBox<String> extensionChoiceBox;

    @FXML
    private Label finishedLabel;

    @FXML
    private Button foldAllButton;

    @FXML
    private ComboBox<String> defaultChapterTitleComboBox;

    @FXML
    private Button splitChaptersButton;

    @FXML
    private ChoiceBox<String> splitMinMaxChoiceBox;

    @FXML
    private Spinner<Integer> splitNumberSpinner;

    @FXML
    private ChoiceBox<String> splitMinutesTracksChoiceBox;

    @FXML
    private ComboBox<String> splitSuffixComboBox;


    private MediaPlayer player;

    private boolean currentlyEditing;
    private final Map<ITunesMetaDataTag, StringProperty> m4aAlbumTags = new HashMap<>();
    private boolean loading = false;
    private boolean foldedAll = false;

    public Controller(Stage stage, MP4Service mp4Service) {
        this.stage = stage;
        this.mp4Service = mp4Service;
    }

    private static Boolean filesSortPolicy(TreeTableView<TableEntry> treeTableView) {
        if (treeTableView.getRoot() != null && treeTableView.getRoot().getChildren() != null) {
            treeTableView.getRoot().getChildren().sort(Comparator.comparingInt(o -> o.getChildren().get(0).getValue().getTrackNr()));
            for (TreeItem<TableEntry> chapter : treeTableView.getRoot().getChildren()) {
                if (chapter.getChildren() != null) {
                    chapter.getChildren().sort(Comparator.comparingInt(o -> o.getValue().getTrackNr()));
                }
            }
        }
        return true;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initialize();
    }


    @FXML
    protected void initialize() {
        addChapterButton.setDisable(true);
        deleteChapterButton.setDisable(true);
        initializeTreeTableView();
        initializePlayerPane();
        initializeImage();
        initializeMetadataInputs();
        initializeExtensionsChoiceBox();
        initializeDefaultChapterTitleComboBox();
        initializeAutoSplitter();
        //loadDirectory(new File("M:/Music/Audible/Downloads/Noah Gordon - Der Medicus"));
    }

    private void initializeAutoSplitter() {
        splitMinMaxChoiceBox.getItems().add("min");
        splitMinMaxChoiceBox.getItems().add("max");
        splitMinMaxChoiceBox.setValue("max");

        splitNumberSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, Integer.MAX_VALUE, 30));

        splitMinutesTracksChoiceBox.getItems().add("minutes");
        splitMinutesTracksChoiceBox.getItems().add("tracks");
        splitMinutesTracksChoiceBox.setValue("minutes");

        splitSuffixComboBox.getItems().add(" - Teil ${i}");
        splitSuffixComboBox.getItems().add(" - Part ${i}");
        splitSuffixComboBox.setValue(" - Teil ${i}");
    }

    private void initializeDefaultChapterTitleComboBox() {
        defaultChapterTitleComboBox.getItems().add("$${script:javascript:${i}-1}. Kapitel");
        defaultChapterTitleComboBox.getItems().add("$${script:javascript:${i}-0}. Chapter");
        defaultChapterTitleComboBox.getItems().add("Kapitel ${i}");
        defaultChapterTitleComboBox.getItems().add("Chapter ${i}");
        defaultChapterTitleComboBox.setValue("Kapitel ${i}");
    }

    private void initializeExtensionsChoiceBox() {
        extensionChoiceBox.getItems().add(".m4b");
        extensionChoiceBox.getItems().add(".m4a");
        extensionChoiceBox.setValue(".m4b");
    }

    private void initializeMetadataInputs() {
        StringProperty bookTitleProperty = new SimpleStringProperty();
        m4aAlbumTags.put(ITunesMetaDataTag.ALBUM_TITLE, bookTitleProperty);
        Bindings.bindBidirectional(bookTitleProperty, bookTitleTextField.textProperty());
        bookTitleProperty.addListener((observable, oldValue, newValue) -> this.saveToFile());

        StringProperty authorProperty = new SimpleStringProperty();
        m4aAlbumTags.put(ITunesMetaDataTag.COMPOSER, authorProperty);
        Bindings.bindBidirectional(authorProperty, authorTextField.textProperty());
        authorProperty.addListener((observable, oldValue, newValue) -> this.saveToFile());

        StringProperty speakerProperty = new SimpleStringProperty();
        m4aAlbumTags.put(ITunesMetaDataTag.ALBUM_INTERPRET, speakerProperty);
        m4aAlbumTags.put(ITunesMetaDataTag.INTERPRET, speakerProperty);
        Bindings.bindBidirectional(speakerProperty, speakerTextField.textProperty());
        speakerProperty.addListener((observable, oldValue, newValue) -> this.saveToFile());

        for (int y = LocalDate.now().getYear(); y >= 1970; y--) {
            yearComboBox.getItems().add(Integer.toString(y));
        }
        StringProperty yearProperty = new SimpleStringProperty();
        m4aAlbumTags.put(ITunesMetaDataTag.YEAR, yearProperty);
        Bindings.bindBidirectional(yearProperty, yearComboBox.valueProperty());
        yearProperty.addListener((observable, oldValue, newValue) -> this.saveToFile());
    }

    private void initializeImage() {
        coverHBox.prefWidthProperty().bind(coverPane.widthProperty());
        coverHBox.prefHeightProperty().bind(coverPane.heightProperty());
        cover.fitWidthProperty().bind(coverPane.widthProperty());
        cover.fitHeightProperty().bind(coverPane.heightProperty());
        m4aAlbumTags.put(ITunesMetaDataTag.COVER, new SimpleStringProperty());
        imageBytes.addListener((observable, oldImage, newImage) -> {
            m4aAlbumTags.get(ITunesMetaDataTag.COVER).set(Base64.getEncoder().encodeToString(newImage));
        });
        setDummyImage();
        cover.imageProperty().bind(
                Bindings.createObjectBinding(() -> new Image(new ByteArrayInputStream(imageBytes.get())), imageBytes)
        );
    }

    private void setDummyImage() {
        InputStream resourceAsStream = getClass().getClassLoader().getResourceAsStream("no_image.png");
        assert resourceAsStream != null;
        try {
            this.imageBytes.setValue(resourceAsStream.readAllBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initializePlayerPane() {
        playerPane.setVisible(false);
        timeSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (timeSlider.isValueChanging()) {
                //player.pause();
                player.seek(javafx.util.Duration.seconds((double) newValue));
            }
            refreshPlayerPane();
        });
    }


    private void initializeTreeTableView() {
        files.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        files.getSelectionModel().getSelectedItems().addListener((ListChangeListener<TreeItem<TableEntry>>) change -> {
            //noinspection StatementWithEmptyBody
            while (change.next()) ;
            System.out.println("File change occurred");
            ObservableList<? extends TreeItem<TableEntry>> changeList = change.getList();
            if (!changeList.isEmpty()) {
                onFileSelectionChange(changeList.get(0));
            }
        });

        files.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (!this.currentlyEditing)
                switch (event.getCode()) {
                    case SPACE:
                        playButton.fire();
                        event.consume();
                        break;
                    case LEFT:
                        prevButton.fire();
                        event.consume();
                        break;
                    case RIGHT:
                        nextButton.fire();
                        event.consume();
                        break;
                    case PLUS:
                    case ADD:
                        addChapterButton.fire();
                        event.consume();
                        break;
                    case F2:
                        editSelectedChapter();
                        event.consume();
                        break;
                }
        });
        files.setEditable(true);

        TreeTableColumn<TableEntry, String> colFilename = new TreeTableColumn<>("Filename");
        colFilename.setPrefWidth(300);
        colFilename.setSortable(false);
        colFilename.setCellValueFactory(new TreeItemPropertyValueFactory<>("filename"));
        colFilename.setEditable(true);
        Callback<TreeTableColumn<TableEntry, String>, TreeTableCell<TableEntry, String>> defaultCellFactory = TextFieldTreeTableCell.forTreeTableColumn();
        colFilename.setCellFactory(c -> {
            TreeTableCell<TableEntry, String> cell = defaultCellFactory.call(c);
            cell.indexProperty().addListener((obs, oldIndex, newIndex) -> {
                TreeItem<TableEntry> item = cell.getTreeTableView().getTreeItem(newIndex.intValue());
                boolean canEdit = item != null && item.getValue().getType() == EntryType.CHAPTER;
                cell.setEditable(canEdit);
            });
            return cell;
        });

        colFilename.setOnEditStart((event -> this.currentlyEditing = true));
        colFilename.setOnEditCancel((event -> {
            currentlyEditing = false;
            files.requestFocus();
        }));
        colFilename.setOnEditCommit((event -> {
            event.getTreeTablePosition().getTreeItem().getValue().setFilename(event.getNewValue());
            this.currentlyEditing = false;
            files.requestFocus();
            updateChapterMeta();
            saveToFile();
        }));
        files.getColumns().add(colFilename);

        TreeTableColumn<TableEntry, Integer> colTrackNr = new TreeTableColumn<>("#");
        colTrackNr.setPrefWidth(30);
        colTrackNr.setSortable(false);
        colTrackNr.setCellValueFactory(new TreeItemPropertyValueFactory<>("trackNr"));
        files.getColumns().add(colTrackNr);

        TreeTableColumn<TableEntry, String> colLength = new TreeTableColumn<>("Length");
        colLength.setCellValueFactory(param -> {
            String duration = DurationFormatUtils.formatDuration(param.getValue().getValue().getLength().getSeconds() * 1000L, "HH:mm:ss");
            return new SimpleStringProperty(duration);
        });
        colLength.setSortable(false);
        files.getColumns().add(colLength);

        TreeTableColumn<TableEntry, Double> colProgress = new TreeTableColumn<>("Progress");
        colProgress.setCellValueFactory(new TreeItemPropertyValueFactory<>("progress"));
        colProgress.setCellFactory(ProgressBarTreeTableCell.forTreeTableColumn());
        colProgress.setSortable(false);
        colProgress.prefWidthProperty().bind(
                files.widthProperty()
                        .subtract(16)
                        .subtract(colFilename.widthProperty())
                        .subtract(colTrackNr.widthProperty())
                        .subtract(colLength.widthProperty())
        );
        files.getColumns().add(colProgress);

        files.setSortPolicy(Controller::filesSortPolicy);
        //files.setSortMode();

        TreeItem<TableEntry> audiobookItem = new TreeItem<>(new Audiobook());
        audiobookItem.setExpanded(true);
//        TreeItem<TableEntry> chapterItem = new TreeItem<>(new Chapter("Book.m4a"));
//        chapterItem.setExpanded(true);
//        TreeItem<TableEntry> track1Item = new TreeItem<>(new Track("Chapter 01.m4a", Duration.ofSeconds(120)));
//        TreeItem<TableEntry> track2Item = new TreeItem<>(new Track("Chapter 02.m4a", Duration.ofSeconds(60)));

        files.setRoot(audiobookItem);
//        audiobookItem.getChildren().add(chapterItem);
//        chapterItem.getChildren().addAll(track1Item, track2Item);
//        files.setRoot(chapterItem);
    }

    @FXML
    protected void timeSliderClicked(Event event) {
        timeSlider.setValueChanging(true);
        double value = (((MouseEvent) event).getX() / timeSlider.getWidth()) * timeSlider.getMax();
        player.seek(javafx.util.Duration.seconds(value));
        timeSlider.setValueChanging(false);
    }

    @FXML
    protected void changeWorkingDirectoryClicked(ActionEvent event) throws ReadOnlyFileException, CannotReadException, TagException, InvalidAudioFrameException, IOException {
        DirectoryChooser fileChooser = new DirectoryChooser();
        fileChooser.setTitle("Select Audiobook Files");
        fileChooser.setInitialDirectory(new File("C:/Users/Niklas/Audible/Downloads"));
        File directory = fileChooser.showDialog(stage);
        if (directory != null) {
            loadDirectory(directory);
        }
    }

    private void loadDirectory(File directory) throws ReadOnlyFileException, IOException, TagException, InvalidAudioFrameException, CannotReadException {
        this.loading = true;
        this.workingDirectoryTextField.setText(directory.getPath());
        this.outputDirectoryTextField.setText(directory.getPath() + File.separator + "merged");
        List<File> covers = List.of(Objects.requireNonNull(directory.listFiles((dir, name) -> name.contains("cover"))));
        if (covers.size() >= 1) {
            try {
                this.imageBytes.set((new FileInputStream(covers.get(0)).readAllBytes()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            setDummyImage();
        }
        List<File> saveFiles = List.of(Objects.requireNonNull(directory.listFiles((dir, name) -> name.endsWith(".m4am"))));
        //printAllTagsOfFile(List.of(Objects.requireNonNull(directory.listFiles((dir, name) -> name.endsWith(".m4a")))).get(0));
        if (saveFiles.size() == 1) {
            loadFromSaveFile(saveFiles.get(0));
        } else {
            List<File> files = List.of(Objects.requireNonNull(directory.listFiles((dir, name) -> name.endsWith(".m4a"))));
            if (!files.isEmpty()) {
                loadTags(files.get(0));
                loadFiles(files);
            } else {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Working directory empty!");
                alert.setHeaderText("Working directory empty!");
                alert.setContentText("Working directory does not contain any .m4a files!");
                ButtonType buttonTypeOk = new ButtonType("Ok", ButtonBar.ButtonData.OK_DONE);
                alert.getButtonTypes().setAll(buttonTypeOk);
                alert.show();
            }
        }
        this.loading = false;
        this.setUIToNotMerging();
    }

    private void loadTags(File file) {
        try {
            MetadataEditor metadataEditor = MetadataEditor.createFrom(file);
            Map<Integer, MetaValue> iTunesMeta = metadataEditor.getItunesMeta();
            resetM4ATags();
            if (iTunesMeta != null) {
                for (Map.Entry<Integer, MetaValue> entry : iTunesMeta.entrySet()) {
                    ITunesMetaDataTag key = ITunesMetaDataTag.fromKey(entry.getKey());
                    if (ITunesMetaDataTag.getAlbumTags().contains(key)) {
                        String stringValue;
                        int type = entry.getValue().getType();
                        switch (type) {
                            case MetaValue.TYPE_STRING_UTF8:
                            case MetaValue.TYPE_STRING_UTF16:
                                stringValue = entry.getValue().getString();
                                break;
                            case MetaValue.TYPE_JPEG:
                                stringValue = Base64.getEncoder().encodeToString(entry.getValue().getData());
                                break;
                            default:
                                throw new IllegalArgumentException("Unhandled field type: " + type);
                        }
                        setM4ATag(key, stringValue);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Swap interpret and composer
        // String tmp = m4aAlbumTags.get(ITunesMetaDataTag.ALBUM_INTERPRET).get();
        // m4aAlbumTags.get(ITunesMetaDataTag.ALBUM_INTERPRET).set(m4aAlbumTags.get(ITunesMetaDataTag.COMPOSER).get());
        // m4aAlbumTags.get(ITunesMetaDataTag.COMPOSER).set(tmp);
    }

    private void setM4ATag(ITunesMetaDataTag key, String value) {
        if (!m4aAlbumTags.containsKey(key)) {
            m4aAlbumTags.put(key, new SimpleStringProperty());
        }
        m4aAlbumTags.get(key).set(value);
    }

    private void resetM4ATags() {
        Collection<ITunesMetaDataTag> delete = new HashSet<>();
        for (Map.Entry<ITunesMetaDataTag, StringProperty> entry : m4aAlbumTags.entrySet()) {
            switch (entry.getKey()) {
                case COVER:
                case COMPOSER:
                case YEAR:
                case INTERPRET:
                case ALBUM_TITLE:
                case ALBUM_INTERPRET:
                    entry.getValue().set("");
                    break;
                default:
                    delete.add(entry.getKey());
            }
        }
        for (ITunesMetaDataTag iTunesMetaDataTag : delete) {
            m4aAlbumTags.remove(iTunesMetaDataTag);
        }
    }

    private void loadFromSaveFile(File saveFile) {
        this.files.getRoot().getChildren().removeAll(this.files.getRoot().getChildren());
        try (BufferedReader br = new BufferedReader(new FileReader(saveFile))) {
            String line;
            int chapterNumber = 1;
            TreeItem<TableEntry> chapterItem = null;
            resetM4ATags();
            while ((line = br.readLine()) != null) {
                if (line.matches("^# [A-Z_]+: .*$")) {
                    Pattern pattern = Pattern.compile("^# (?<key>[A-Z_]+): (?<value>.*)$");
                    Matcher matcher = pattern.matcher(line);
                    while (matcher.find()) {
                        ITunesMetaDataTag key = ITunesMetaDataTag.valueOf(matcher.group("key"));
                        String value = matcher.group("value");
                        setM4ATag(key, value);
                    }
                } else if (line.startsWith("\t")) {
                    assert chapterItem != null;
                    try {
                        File file = new File(workingDirectoryTextField.getText() + File.separator + line.trim());
                        AudioFile audioFile = AudioFileIO.read(file);
                        String title = audioFile.getTag().getFields(FieldKey.TITLE).get(0).toString();
                        Duration length = Duration.ofSeconds(audioFile.getAudioHeader().getTrackLength());
                        int trackNr = Integer.valueOf(audioFile.getTag().getFields(FieldKey.TRACK).get(0).toString().split("/")[0]);
                        TreeItem<TableEntry> trackEntry = new TreeItem<>(new Track(file.getName(), length, file, trackNr));
                        chapterItem.getChildren().add(trackEntry);
                        chapterItem.getValue().setLength(chapterItem.getValue().getLength().plus(length));
                    } catch (CannotReadException | IOException | TagException | ReadOnlyFileException | InvalidAudioFrameException e) {
                        e.printStackTrace();
                    }
                } else {
                    chapterItem = new TreeItem<>(new Chapter(line, chapterNumber++));
                    chapterItem.setExpanded(true);
                    this.files.getRoot().getChildren().add(chapterItem);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    protected void playButtonClicked(ActionEvent event) {
        if (player.getStatus() == MediaPlayer.Status.PLAYING) {
            player.pause();
        } else {
            player.play();
        }
    }

    @FXML
    protected void stopButtonClicked(ActionEvent event) {
        player.stop();
        refreshPlayerPane();
    }

    @FXML
    protected void addChapterClicked(ActionEvent event) {
        TreeItem<TableEntry> selectedItem = this.files.getSelectionModel().getSelectedItem();
        addChapter(selectedItem, "");
    }

    private void addChapter(TreeItem<TableEntry> firstItem, String chapterName) {
        if (firstItem != null && firstItem.getValue().getType() == EntryType.TRACK) {
            Iterator<TreeItem<TableEntry>> siblingIterator = firstItem.getParent().getChildren().iterator();
            TreeItem<TableEntry> sibling = siblingIterator.next();
            if (sibling != firstItem) {
                while (sibling != firstItem) {
                    sibling = siblingIterator.next();
                }

                TreeItem<TableEntry> newChapter = new TreeItem<>(new Chapter(chapterName, firstItem.getValue().getTrackNr()));
                newChapter.setExpanded(true);
                this.files.getRoot().getChildren().add(newChapter);
                siblingIterator.remove();
                newChapter.getChildren().add(sibling);

                while (siblingIterator.hasNext()) {
                    TreeItem<TableEntry> track = siblingIterator.next();
                    siblingIterator.remove();
                    newChapter.getChildren().add(track);
                }
                updateChapterMeta();
                this.files.getSelectionModel().select(newChapter);
                this.onFileSelectionChange(files.getSelectionModel().getSelectedItem());
                editSelectedChapter();
            }
        }
        saveToFile();
    }

    private void editSelectedChapter() {
        Platform.runLater(() -> {
            TreeTableView.TreeTableViewSelectionModel<TableEntry> selectionModel = this.files.getSelectionModel();
            if (selectionModel.getSelectedItem().getValue().getType() == EntryType.CHAPTER) {
                this.files.edit(selectionModel.getSelectedIndex(), this.files.getColumns().get(0));
            }
        });
    }

    private void updateChapterMeta() {
        files.sort();
        int chapterNr = 1;
        for (TreeItem<TableEntry> chapter : files.getRoot().getChildren()) {
            chapter.getValue().setLength(Duration.ZERO);
            if ("".equals(chapter.getValue().getFilename())) {
                Map<String, String> valuesMap = new HashMap<>();
                valuesMap.put("i", String.valueOf(chapterNr));
                final StringSubstitutor substitutor = new StringSubstitutor(valuesMap);
                substitutor.setEnableSubstitutionInVariables(true); // Allows for nested $'s.
                String title = substitutor.replace(defaultChapterTitleComboBox.getValue());
                final StringSubstitutor interpolator = StringSubstitutor.createInterpolator();
                title = interpolator.replace(title);
                chapter.getValue().setFilename(title);
            }
            ((Chapter) chapter.getValue()).setTrackNr(chapterNr++);
            for (TreeItem<TableEntry> track : chapter.getChildren()) {
                chapter.getValue().setLength(chapter.getValue().getLength().plus(track.getValue().getLength()));
            }
        }
        files.refresh();
    }

    @FXML
    private void deleteChapterClicked(ActionEvent event) {
        TreeItem<TableEntry> selectedItem = this.files.getSelectionModel().getSelectedItem();
        deleteChapter(selectedItem);
    }

    @FXML
    private void deleteAllChapterClicked(ActionEvent event) {
        while (files.getRoot().getChildren().size() > 1) {
            deleteChapter(files.getRoot().getChildren().get(1));
        }
    }

    private void deleteChapter(TreeItem<TableEntry> chapterItem) {
        if (chapterItem.getValue().getType() == EntryType.CHAPTER) {
            Iterator<TreeItem<TableEntry>> chapterIterator = this.files.getRoot().getChildren().iterator();
            TreeItem<TableEntry> prevChapter = chapterIterator.next();
            if (chapterIterator.hasNext()) {
                TreeItem<TableEntry> chapter = chapterIterator.next();
                while (chapter != chapterItem) {
                    prevChapter = chapter;
                    chapter = chapterIterator.next();
                }
                ObservableList<TreeItem<TableEntry>> children = chapterItem.getChildren();
                this.files.getRoot().getChildren().remove(chapterItem);
                prevChapter.getChildren().addAll(children);
                this.files.getSelectionModel().select(children.get(0));
                this.onFileSelectionChange(files.getSelectionModel().getSelectedItem());
            }
            updateChapterMeta();
        }
        saveToFile();
    }

    private void loadFiles(List<File> files) throws TagException, ReadOnlyFileException, CannotReadException, InvalidAudioFrameException, IOException {
        if (files != null) {
            Thread thread = new Thread(() -> {
                this.files.getRoot().getChildren().removeAll(this.files.getRoot().getChildren());
                TreeItem<TableEntry> chapterItem = new TreeItem<>(new Chapter("Chapter 1", 1));
                chapterItem.setExpanded(true);
                this.files.getRoot().getChildren().add(chapterItem);
                for (File file : files) {
                    try {
                        AudioFile audioFile = AudioFileIO.read(file);
                        //String title = audioFile.getTag().getFields(FieldKey.TITLE).get(0).toString();
                        Duration length = Duration.ofSeconds(audioFile.getAudioHeader().getTrackLength());
                        int trackNr = Integer.parseInt(audioFile.getTag().getFields(FieldKey.TRACK).get(0).toString());
                        TreeItem<TableEntry> trackEntry = new TreeItem<>(new Track(file.getName(), length, file, trackNr));
                        chapterItem.getChildren().add(trackEntry);
                        chapterItem.getValue().setLength(chapterItem.getValue().getLength().plus(length));
                    } catch (CannotReadException | IOException | TagException | ReadOnlyFileException | InvalidAudioFrameException e) {
                        e.printStackTrace();
                    }
                }
                this.files.sort();
                updateChapterMeta();
            });
            thread.start();
        }
    }

    @FXML
    private void onFileSelectionChange(TreeItem<TableEntry> selectedItem) {
        if (selectedItem != null) {
            TableEntry selectedEntry = selectedItem.getValue();
            System.out.println("File Selected: " + selectedEntry.getFilename());
            try {
                switch (selectedEntry.getType()) {
                    case AUDIOBOOK:
                        assert false;
                        break;
                    case CHAPTER:
                        updateUIForChapter((Chapter) selectedEntry);
                        loadFileToPlayer(((Track) selectedItem.getChildren().get(0).getValue()).getFile());
                        break;
                    case TRACK:
                        updateUIForTrack((Track) selectedEntry);
                        loadFileToPlayer(((Track) selectedEntry).getFile());
                        break;
                }
            } catch (ReadOnlyFileException | IOException | TagException | InvalidAudioFrameException | CannotReadException e) {
                e.printStackTrace();
            }
        } else {
            unloadPlayer();
        }
    }

    private void unloadPlayer() {
        playerPane.setVisible(false);
        if (player != null) {
            player.dispose();
            player = null;
        }
    }

    private void updateUIForTrack(Track track) throws ReadOnlyFileException, IOException, TagException, InvalidAudioFrameException, CannotReadException {
        addChapterButton.setDisable(false);
        deleteChapterButton.setDisable(true);
        playerPane.setVisible(true);
    }

    private void updateUIForChapter(Chapter chapter) {
        addChapterButton.setDisable(true);
        if (chapter.getTrackNr() == 1) {
            deleteChapterButton.setDisable(true);
        } else {
            deleteChapterButton.setDisable(false);
        }
        playerPane.setVisible(true);
    }

    private void loadFileToPlayer(File file) throws TagException, ReadOnlyFileException, CannotReadException, InvalidAudioFrameException, IOException {
        String name = file.toURI().toString();
        fileNameLabel.setText(file.getName());
        //name = new File("M:/Music/MusicBee/Music/Annett Louisan/Bohème/01 Das Spiel.mp3").toURI().toString();
        boolean wasPlaying = false;
        if (player != null) {
            wasPlaying = player.getStatus() == MediaPlayer.Status.PLAYING;
            player.stop();
            player.dispose();
        }
        Media song = new Media(name);
        player = new MediaPlayer(song);
        player.setAutoPlay(false);
        player.currentTimeProperty().addListener((observable, oldValue, newValue) -> refreshPlayerPane());
        player.setOnEndOfMedia(this::mediaPlayerEndOfMedia);
        player.setOnError(this::mediaPlayerError);
        player.setOnPlaying(this::mediaPlayerPlaying);
        player.setOnPaused(this::mediaPlayerPaused);
        player.setOnStopped(this::mediaPlayerStopped);

        timeSlider.setMin(0);
        //timeSlider.setMajorTickUnit(60);
        //timeSlider.setMinorTickCount(60);
        int trackLength = AudioFileIO.read(file).getAudioHeader().getTrackLength();
        //TODO:pr
        //MP4Container mp4Container = new MP4Container(new FileInputStream(file));
        //net.sourceforge.jaad.mp4.api.Track track = mp4Container.getMovie().getTracks(AudioTrack.AudioCodec.AAC).get(0);

        timeSlider.setMax(trackLength);
        durationLabel.setText(DurationFormatUtils.formatDuration(trackLength * 1000L, "HH:mm:ss"));

        refreshPlayerPane();
        if (wasPlaying) {
            player.play();
        }
    }

    private void refreshPlayerPane() {
        //Platform.runLater(() -> {
        javafx.util.Duration currentTime = player.getCurrentTime();
        currentTimeLabel.setText(DurationFormatUtils.formatDuration((long) currentTime.toMillis(), "HH:mm:ss"));
        //if (!timeSlider.isValueChanging()) {
        //    timeSlider.setValue(currentTime.toSeconds());
        //}
        if (player.getStatus() == MediaPlayer.Status.PLAYING) {
            playButton.setText(PAUSE_SYMBOL);
        } else {
            playButton.setText(PLAY_SYMBOL);
        }
        currentTimeLabel.setText(DurationFormatUtils.formatDuration((long) player.getCurrentTime().toMillis(), "HH:mm:ss"));
        timeSlider.setValue(player.getCurrentTime().toSeconds());
        //});
    }


    private void mediaPlayerEndOfMedia() {
        System.out.println("Media ended");
        selectNextMediaFile();
    }

    private void selectNextMediaFile() {
        this.files.getSelectionModel().selectNext();
    }

    private void selectPreviousMediaFile() {
        this.files.getSelectionModel().selectPrevious();
    }

    private void mediaPlayerError() {
        System.out.println("Media Error");
        refreshPlayerPane();
        player.getError().printStackTrace();
    }

    private void mediaPlayerPaused() {
        System.out.println("Media paused");
        refreshPlayerPane();
    }

    private void mediaPlayerPlaying() {
        System.out.println("Media playing");
        refreshPlayerPane();
    }

    private void mediaPlayerStopped() {
        System.out.println("Media stopped");
        refreshPlayerPane();
    }

    @FXML
    private void mergeClicked(ActionEvent actionEvent) throws IOException {
        File outputDir = new File(outputDirectoryTextField.getText());
        if (outputDir.exists() && !outputDir.isDirectory()) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Output Directory no directory");
            alert.setContentText("The output directory already exists but is no directory.");
            alert.show();
            return;
        }
        String[] fileListInOutputdir = outputDir.list();
        if (outputDir.exists() && fileListInOutputdir != null && fileListInOutputdir.length > 0) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Output directory not empty");
            alert.setHeaderText("Output directory is not empty. Delete contents?");
            alert.setContentText("Output directory \"" + outputDir.getPath() + "\" is not empty. Delete contents?");
            ButtonType buttonTypeYes = new ButtonType("Yes", ButtonBar.ButtonData.YES);
            ButtonType buttonTypeNo = new ButtonType("No", ButtonBar.ButtonData.NO);
            ButtonType buttonTypeCancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(buttonTypeYes, buttonTypeNo, buttonTypeCancel);
            Optional<ButtonType> result = alert.showAndWait();
            if (!result.isPresent() || result.get() == buttonTypeCancel) {
                return;
            } else if (result.get() == buttonTypeYes) {
                for (File file : outputDir.listFiles()) {
                    Files.delete(file.toPath());
                }
            }
        }
        setUIToMerging();
        ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        for (TreeItem<TableEntry> chapter : files.getRoot().getChildren()) {
            executorService.execute(() -> {
                List<Track> tracks = chapter.getChildren().stream().map(trackTreeItem -> (Track) trackTreeItem.getValue()).collect(Collectors.toList());
                try {
                    mp4Service.merge(outputDirectoryTextField.getText(), (Chapter) chapter.getValue(), tracks, m4aAlbumTags, extensionChoiceBox.getValue());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
        executorService.shutdown();
        mergeButton.setDisable(false);
    }

    private void setUIToMerging() {
        finishedLabel.setVisible(false);
        mergeButton.setVisible(false);
        mergeProgressBar.setVisible(true);
        unloadPlayer();
        Timer t = new Timer();
        t.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                ObservableList<TreeItem<TableEntry>> chapterItems = files.getRoot().getChildren();
                double totalProgress = chapterItems.stream()
                        .mapToDouble((obj) -> obj.getValue().getProgress())
                        .average().orElse(0);
                mergeProgressBar.setProgress(totalProgress);
                if (totalProgress == 1d) {
                    t.cancel();
                    java.awt.Toolkit.getDefaultToolkit().beep();
                    finishedLabel.setVisible(true);
                }
            }
        }, 0, 100);
    }

    private void setUIToNotMerging() {
        finishedLabel.setVisible(false);
        mergeButton.setVisible(true);
        mergeProgressBar.setVisible(false);
        for (TreeItem<TableEntry> chapter : files.getRoot().getChildren()) {
            chapter.getValue().setProgress(0);
            for (TreeItem<TableEntry> track : chapter.getChildren()) {
                track.getValue().setProgress(0);
            }
        }
    }

    @FXML
    private void chooseDirectoryClicked(ActionEvent actionEvent) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        File directory = new File(outputDirectoryTextField.getText());
        directory.mkdirs();
        directoryChooser.setInitialDirectory(directory);
        File file = directoryChooser.showDialog(stage);
        if (file != null) {
            outputDirectoryTextField.setText(file.getPath());
        }
    }

    @FXML
    private void coverClicked() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Cover");
        fileChooser.getExtensionFilters().addAll(
                new ExtensionFilter("Picture Files", "*.jpeg", "*.jpg", "*.png"),
                new ExtensionFilter("All Files", "*"));
        fileChooser.setInitialDirectory(new File("C:/Users/Niklas/Audible/Downloads"));
        File file = fileChooser.showOpenDialog(stage);
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            this.imageBytes.set(fileInputStream.readAllBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void prevButtonClicked(ActionEvent event) {
        selectPreviousMediaFile();
    }

    @FXML
    private void nextButtonClicked(ActionEvent event) {
        selectNextMediaFile();
    }

    private void saveToFile() {
        if (!loading) {
            String wdir = workingDirectoryTextField.getText();
            String pathname = wdir + File.separator + wdir.substring(wdir.lastIndexOf(File.separator)) + ".m4am";
            try (final PrintWriter writer = new PrintWriter(new FileWriter(new File(pathname)))) {
                for (Map.Entry<ITunesMetaDataTag, StringProperty> entry : m4aAlbumTags.entrySet()) {
                    writer.println("# " + entry.getKey().toString() + ": " + entry.getValue().get());
                }
                for (TreeItem<TableEntry> chapter : files.getRoot().getChildren()) {
                    writer.println(chapter.getValue().getFilename());
                    for (TreeItem<TableEntry> track : chapter.getChildren()) {
                        writer.println("\t" + track.getValue().getFilename());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void printAllTagsOfFile(File file) {
        try {
            MetadataEditor metadataEditor = MetadataEditor.createFrom(file);
            Map<Integer, MetaValue> iTunesMeta = metadataEditor.getItunesMeta();
            System.out.println("File \"" + file.getName() + "\":");
            if (iTunesMeta != null) {
                for (Map.Entry<Integer, MetaValue> entry : iTunesMeta.entrySet()) {
                    System.out.println("\t0x" + Integer.toHexString(entry.getKey()) + " (" + entry.getValue().getType() + "): " + Arrays.toString(entry.getValue().getData()) + " = " + entry.getValue());
                }
            }
            System.out.println();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void foldAllButtonClicked(ActionEvent event) {
        if (this.foldedAll) {
            for (TreeItem<TableEntry> chapterItem : this.files.getRoot().getChildren()) {
                chapterItem.setExpanded(true);
            }
            this.foldedAll = false;
            this.foldAllButton.setText("Fold all");
        } else {
            for (TreeItem<TableEntry> chapterItem : this.files.getRoot().getChildren()) {
                chapterItem.setExpanded(false);
            }
            this.foldedAll = true;
            this.foldAllButton.setText("Unfold all");
        }
    }

    @FXML
    private void splitChaptersButtonClicked(ActionEvent event) {
        for (int c = 0; c < files.getRoot().getChildren().size(); c++) {
            TreeItem<TableEntry> chapterItem = files.getRoot().getChildren().get(c);
            if (exceedsLimit(chapterItem)) {
                String chapterName = chapterItem.getValue().getFilename();
                List<List<TreeItem<TableEntry>>> trackLists = getSplitTrackLists(chapterItem);
                for (int i = 0; i < trackLists.size(); i++) {
                    Map<String, String> valueMap = Collections.singletonMap("i", Integer.toString(i + 1));
                    StringSubstitutor substitutor = new StringSubstitutor(valueMap);
                    String newChapterName = chapterName + substitutor.replace(splitSuffixComboBox.getValue());
                    if (i == 0) {
                        chapterItem.getValue().setFilename(newChapterName);
                    } else {
                        addChapter(trackLists.get(i).get(0), newChapterName);
                    }
                }
                c += trackLists.size() - 1;
            }
        }
    }

    private List<List<TreeItem<TableEntry>>> getSplitTrackLists(TreeItem<TableEntry> chapterItem) {
        List<List<TreeItem<TableEntry>>> trackLists = new LinkedList<>();
        ListIterator<TreeItem<TableEntry>> iterator = chapterItem.getChildren().listIterator();
        while (iterator.hasNext()) {
            List<TreeItem<TableEntry>> trackList = new LinkedList<>();
            Duration durationTrackList = Duration.ZERO;
            while (iterator.hasNext()) {
                TreeItem<TableEntry> trackItem = iterator.next();
                Track track = (Track) trackItem.getValue();
                if (exceedsLimit(durationTrackList.plus(track.getLength()), trackList.size() + 1)) {
                    if (splitMinMaxChoiceBox.getValue().equals("min")) {
                        trackList.add(trackItem);
                    } else {
                        iterator.previous();
                    }
                    break;
                }
                trackList.add(trackItem);
                durationTrackList = durationTrackList.plus(track.getLength());
            }
            trackLists.add(trackList);
        }
        return trackLists;
    }

    private boolean exceedsLimit(TreeItem<TableEntry> chapterItem) {
        return exceedsLimit(chapterItem.getValue().getLength(), chapterItem.getChildren().size());
    }

    private boolean exceedsLimit(Duration duration, int numberOfTracks) {
        boolean exceedsLimit =
                splitMinutesTracksChoiceBox.getValue().equals("minutes") &&
                        duration.compareTo(Duration.ofMinutes(splitNumberSpinner.getValue())) > 0;
        exceedsLimit = exceedsLimit ||
                splitMinutesTracksChoiceBox.getValue().equals("tracks") &&
                        numberOfTracks > splitNumberSpinner.getValue();
        return exceedsLimit;
    }
}
