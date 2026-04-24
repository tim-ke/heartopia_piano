package heartopia_piano;

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

public class heartopia_piano {

    private JFrame frame;
    private JTextArea textArea;
    private JSlider speedSlider;
    private JSpinner transposeSpinner;
    private JComboBox<String> modeBox;
    private JButton btnSelect, btnPlay, btnPause, btnStop;
    
    private Robot robot;
    private Map<Integer, Integer> keyMap15;
    private Map<Integer, Integer> keyMapProfessional;
    private final ExecutorService keyExecutor = Executors.newCachedThreadPool();

    private volatile boolean isPlaying = false;
    private volatile boolean isPaused = false;
    private volatile boolean stopRequested = false;
    private final Object pauseLock = new Object();
    private File currentFile = null;

    public static void main(String[] args) {
        if (!checkAndElevate()) return;
        EventQueue.invokeLater(() -> {
            try {
                heartopia_piano window = new heartopia_piano();
                window.frame.setVisible(true);
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    private static boolean checkAndElevate() {
        boolean isAdmin = false;
        try {
            File testFile = new File("C:/Windows/heartopia_admin_test.txt");
            if (testFile.createNewFile()) { testFile.delete(); isAdmin = true; }
        } catch (Exception e) { isAdmin = false; }
        if (!isAdmin) {
            try {
                String jarPath = new File(heartopia_piano.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
                Runtime.getRuntime().exec("powershell -Command \"Start-Process java -ArgumentList '-jar \\\"" + jarPath + "\\\"' -Verb RunAs\"");
                System.exit(0);
            } catch (Exception e) { e.printStackTrace(); }
            return false;
        }
        return true;
    }

    public heartopia_piano() throws AWTException {
        robot = new Robot();
        robot.setAutoDelay(0);
        initKeyMaps();
        initialize();
    }

    private void initKeyMaps() {
        keyMap15 = new HashMap<>();
        int[] notes15 = {60, 62, 64, 65, 67, 69, 71, 72, 74, 76, 77, 79, 81, 83, 84};
        int[] keys15 = {KeyEvent.VK_A, KeyEvent.VK_S, KeyEvent.VK_D, KeyEvent.VK_F, KeyEvent.VK_G, KeyEvent.VK_H, KeyEvent.VK_J, 
                        KeyEvent.VK_Q, KeyEvent.VK_W, KeyEvent.VK_E, KeyEvent.VK_R, KeyEvent.VK_T, KeyEvent.VK_Y, KeyEvent.VK_U, KeyEvent.VK_I};
        for (int i = 0; i < notes15.length; i++) keyMap15.put(notes15[i], keys15[i]);

        keyMapProfessional = new HashMap<>();
        int[][] proConfig = {
            {48, KeyEvent.VK_COMMA}, {49, KeyEvent.VK_L}, {50, KeyEvent.VK_PERIOD}, {51, KeyEvent.VK_SEMICOLON}, {52, KeyEvent.VK_SLASH},
            {53, KeyEvent.VK_O}, {54, KeyEvent.VK_0}, {55, KeyEvent.VK_P}, {56, KeyEvent.VK_MINUS}, {57, KeyEvent.VK_OPEN_BRACKET},
            {58, KeyEvent.VK_EQUALS}, {59, KeyEvent.VK_CLOSE_BRACKET}, {60, KeyEvent.VK_Z}, {61, KeyEvent.VK_S}, {62, KeyEvent.VK_X},
            {63, KeyEvent.VK_D}, {64, KeyEvent.VK_C}, {65, KeyEvent.VK_V}, {66, KeyEvent.VK_G}, {67, KeyEvent.VK_B}, {68, KeyEvent.VK_H},
            {69, KeyEvent.VK_N}, {70, KeyEvent.VK_J}, {71, KeyEvent.VK_M}, {72, KeyEvent.VK_Q}, {73, KeyEvent.VK_2}, {74, KeyEvent.VK_W},
            {75, KeyEvent.VK_3}, {76, KeyEvent.VK_E}, {77, KeyEvent.VK_R}, {78, KeyEvent.VK_5}, {79, KeyEvent.VK_T}, {80, KeyEvent.VK_6},
            {81, KeyEvent.VK_Y}, {82, KeyEvent.VK_7}, {83, KeyEvent.VK_U}, {84, KeyEvent.VK_I}
        };
        for (int[] pair : proConfig) keyMapProfessional.put(pair[0], pair[1]);
    }

    // 將 MIDI 編號轉換為音名與唱名
    private String getNoteLabel(int midiNote) {
        String[] noteNames = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};//音名
        String[] syllables = {"Do", "Do#", "Re", "Re#", "Mi", "Fa", "Fa#", "Sol", "Sol#", "La", "La#", "Si"};//唱名
        int octave = (midiNote / 12) - 1;
        int idx = midiNote % 12;
        return noteNames[idx] + octave + " (" + syllables[idx] + ")";
    }

    private void initialize() {
        frame = new JFrame("Heartopia Piano - 完整輸出優化版");
        frame.setBounds(100, 100, 600, 650);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        JPanel controlPanel = new JPanel(new GridLayout(6, 1));
        
        JPanel filePanel = new JPanel();
        btnSelect = new JButton("選取 MIDI 檔案");
        btnSelect.addActionListener(e -> selectFile());
        filePanel.add(btnSelect);
        controlPanel.add(filePanel);

        JPanel modePanel = new JPanel();
        modePanel.add(new JLabel("彈奏模式："));
        modeBox = new JComboBox<>(new String[]{"15 鍵模式 (過濾根音/自動位移)", "專業模式 (37 鍵/全音彈奏)"});
        modePanel.add(modeBox);
        controlPanel.add(modePanel);

        JPanel transposePanel = new JPanel();
        transposePanel.add(new JLabel("整體移調："));
        transposeSpinner = new JSpinner(new SpinnerNumberModel(0, -24, 24, 1));
        transposePanel.add(transposeSpinner);
        controlPanel.add(transposePanel);

        JPanel speedPanel = new JPanel();
        speedPanel.add(new JLabel("速度："));
        speedSlider = new JSlider(50, 200, 100);
        speedSlider.setMajorTickSpacing(50);
        speedSlider.setPaintTicks(true);
        speedSlider.setPaintLabels(true);
        speedPanel.add(speedSlider);
        controlPanel.add(speedPanel);

        JPanel playbackButtons = new JPanel();
        btnPlay = new JButton("播放"); btnPause = new JButton("暫停"); btnStop = new JButton("停止");
        btnPlay.setEnabled(false); btnPause.setEnabled(false); btnStop.setEnabled(false);
        btnPlay.addActionListener(e -> startPlayAction());
        btnPause.addActionListener(e -> togglePause());
        btnStop.addActionListener(e -> stopPlayback());
        playbackButtons.add(btnPlay); playbackButtons.add(btnPause); playbackButtons.add(btnStop);
        controlPanel.add(playbackButtons);

        frame.add(controlPanel, BorderLayout.NORTH);
        textArea = new JTextArea("💡 系統就緒。請選取 MIDI 檔案後開始。\n");
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        frame.add(new JScrollPane(textArea), BorderLayout.CENTER);
    }

    private void selectFile() {
        FileDialog fd = new FileDialog(frame, "選擇 MIDI", FileDialog.LOAD);
        fd.setFile("*.mid;*.midi");
        fd.setVisible(true);
        if (fd.getFile() != null) {
            currentFile = new File(fd.getDirectory(), fd.getFile());
            textArea.append("\n載入檔案：" + currentFile.getName() + "\n");
            btnPlay.setEnabled(true);
        }
    }

    private void startPlayAction() {
        if (isPlaying) return;
        new Thread(this::startAutoPlay).start();
    }

    private void togglePause() {
        isPaused = !isPaused;
        btnPause.setText(isPaused ? "繼續" : "暫停");
        if (!isPaused) synchronized (pauseLock) { pauseLock.notifyAll(); }
    }

    private void stopPlayback() {
        stopRequested = true;
        isPaused = false;
        synchronized (pauseLock) { pauseLock.notifyAll(); }
    }

    private void startAutoPlay() {
        try {
        	
        	// 取得 MIDI 序列 (Sequence)
            Sequence sequence = MidiSystem.getSequence(currentFile);//把二進制的MIDI打開，變成程式看得懂的序列
            textArea.append("正在解析檔案: " + currentFile.getName() + "\n");
            textArea.append("總音軌數: " + sequence.getTracks().length + "\n");
            int resolution = sequence.getResolution();
            List<MidiEvent> allEvents = new ArrayList<>();
            
            // 遍歷每一個音軌 (Track)
            for (Track track : sequence.getTracks()) {//檔案裡面的音軌，把每一個音軌抓出來
                for (int i = 0; i < track.size(); i++) allEvents.add(track.get(i)); 
                
            }
            
            // 按時間排序，確保根音與旋律同步
            allEvents.sort(Comparator.comparingLong(MidiEvent::getTick));

            double speedFactor = speedSlider.getValue() / 100.0; //速率
            int transposeOffset = (int) transposeSpinner.getValue(); //移調，每次1個音(含半音)，共12個
            int modeIndex = modeBox.getSelectedIndex(); //15

            SwingUtilities.invokeLater(() -> {
                setControlsEnabled(false);
                textArea.append(String.format(">> [參數] 速度:%.1fx | 移調:%d | 模式:%s\n", 
                        speedFactor, transposeOffset, modeBox.getSelectedItem()));
                textArea.setCaretPosition(textArea.getDocument().getLength()); // 自動捲動
            });

            Thread.sleep(3000);//等待x秒後開始執行
            isPlaying = true; stopRequested = false; isPaused = false;
            long lastTick = 0;
            double currentBPM = 120.0;

            for (MidiEvent event : allEvents) {
                if (stopRequested) break;
                synchronized (pauseLock) { while (isPaused) pauseLock.wait(); }

                MidiMessage message = event.getMessage();
                long currentTick = event.getTick();

                // 處理速度變化 (Tempo Change)
                if (message instanceof MetaMessage && ((MetaMessage)message).getType() == 0x51) {
                    byte[] data = ((MetaMessage)message).getData();
                    int tempo = ((data[0] & 0xff) << 16) | ((data[1] & 0xff) << 8) | (data[2] & 0xff);
                    currentBPM = 60000000.0 / tempo;
                }
                              
                //當你看到 NOTE_ON，就代表樂譜上出現了一個音
                if (message instanceof ShortMessage) { //用instanceof檢查轉型成ShortMessage是否不會報錯
                    ShortMessage sm = (ShortMessage) message;//轉型
                    int channel = sm.getChannel();
                    int command = sm.getCommand();
                    int d1 = sm.getData1();
                    int d2 = sm.getData2();
                    System.out.println(sm.getData2());
                    String cmdName = (command == ShortMessage.NOTE_ON) ? "按下" : "放開";
                    
                    if (sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() > 0) {
                        long tickDiff = currentTick - lastTick;
                        if (tickDiff > 0) {
                            long sleepMillis = (long) (((tickDiff * 60000.0) / (currentBPM * resolution)) / speedFactor);
                            if (sleepMillis > 0) Thread.sleep(sleepMillis);
                            lastTick = currentTick;
                        }

                        int originalNote = sm.getData1();
                        int playNote = originalNote + transposeOffset;
                        int finalKeyToPress = -1;
                        String noteInfo = "";

                        if (modeIndex == 0) { // 15 鍵模式
                            if (originalNote < 60) continue; // 過濾掉原始低音根音
                            
                            // 位移規則：加減 12 直到落在 60-84 (C4-C6)
                            while (playNote < 60) playNote += 12;
                            while (playNote > 84) playNote -= 12;
                            
                            if (keyMap15.containsKey(playNote)) {
                                finalKeyToPress = keyMap15.get(playNote);
                                noteInfo = getNoteLabel(playNote);
                            }
                        } else { // 專業模式
                            while (playNote < 48) playNote += 12;
                            while (playNote > 84) playNote -= 12;
                            if (keyMapProfessional.containsKey(playNote)) {
                                finalKeyToPress = keyMapProfessional.get(playNote);
                                noteInfo = getNoteLabel(playNote);
                            }
                        }

                        if (finalKeyToPress != -1) {
                            final int key = finalKeyToPress;
                            // 輸出核心數據快照
                            final String detail = String.format(
                                "[Tick %d] | [頻道:%2d] | 指令:%s | 音高:%3d | 力度:%3d | 彈奏: %-12s | 鍵盤: %s",
                                currentTick, channel, cmdName, d1, d2, noteInfo, KeyEvent.getKeyText(key)
                            );
                             
                            System.out.println(detail);
//                            SwingUtilities.invokeLater(() -> {
//                                textArea.append(log + "\n");
//                                textArea.setCaretPosition(textArea.getDocument().getLength()); // 自動捲動
//                            });

                            keyExecutor.execute(() -> {
                                try {
                                    robot.keyPress(key);
                                    Thread.sleep(50);
                                    robot.keyRelease(key);
                                } catch (Exception ignored) {}
                            });
                        }
                    }
                }else if (message instanceof MetaMessage) {
                    MetaMessage mm = (MetaMessage) message;
                    int type = mm.getType();
                    byte[] data = mm.getData();

                    // 解析速度 (Type 0x51)
                    if (type == 0x51) {
                        int tempo = ((data[0] & 0xff) << 16) | ((data[1] & 0xff) << 8) | (data[2] & 0xff);
                        int bpm = 60000000 / tempo;
                        System.out.println(">> 系統速度變更: " + bpm + " BPM");
                    }
                    // 解析拍號 (Type 0x58)
                    else if (type == 0x58) {
                        System.out.println(String.format(">> 拍號變更: %d/%d 拍", data[0], (int)Math.pow(2, data[1])));
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        finally {
            isPlaying = false;
            SwingUtilities.invokeLater(() -> {
                setControlsEnabled(true);
                textArea.append(">> 播放結束。\n");
                textArea.append("------------------------------------------\n");
            });
        }
    }
    

    private void setControlsEnabled(boolean b) {
        btnSelect.setEnabled(b); modeBox.setEnabled(b); transposeSpinner.setEnabled(b);
        speedSlider.setEnabled(b); btnPlay.setEnabled(b);
        btnPause.setEnabled(!b); btnStop.setEnabled(!b);
    }
}