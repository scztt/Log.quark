Log : Singleton {
    classvar defaultFormatter, splitLineFormatter, onErrorAction, <levels, exceptionHandler, globalLogFile;
    var <>actions, <>formatter, <>shouldPost = true, <>maxLength = 500, <lines, <level, levelNum,
        <splitLines=false, <>unprintedLine="", logFile, logToFileAction;
    
    *initClass {
        Class.initClassTree(Singleton);
        
        defaultFormatter = {
            |item, log|
            "[%] ".format(log.name.asString().toUpper()).padRight(12) ++ item[\string];
        };
        
        splitLineFormatter = {
            |item, log|
            var logOutput;
            
            log.unprintedLine = log.unprintedLine ++ item[\string];
            
            if (log.unprintedLine.contains("\n")) {
                log.unprintedLine = log.unprintedLine.split(Char.nl);
                logOutput = log.unprintedLine[0..(log.unprintedLine.size - 2)].collect({
                    |line|
                    "[%] ".format(log.name.asString().toUpper()).padRight(12) ++ line;
                }).join("\n");
                log.unprintedLine = log.unprintedLine.last;
                
                logOutput
            }
        };
        
        levels = (
            all: -999,
            debug: 0,
            info: 1,
            warning: 3,
            error: 6,
            off: 999
        );
    }
    
    *globalLogFile {
        globalLogFile ?? {
            globalLogFile = File.open("~/Desktop/sclang-Log-quark.log".standardizePath, "a");
        };
        
        ^globalLogFile
    }
    
    *logErrors {
        | shouldLog = true |
        var rootThread = thisThread, handler;
        
        while { rootThread.parent.notNil } {
            rootThread = rootThread.parent;
        };
        
        if (shouldLog) {
            exceptionHandler = {
                | exc |
                try {
                    Log(\error).error(exc.errorString.replace("ERROR: ", ""));
                };
                
                rootThread.parent.handleError(exc);
            };
            
            rootThread.exceptionHandler = exceptionHandler;
            
            OnError.add(onErrorAction = {
                Log(\error, "---");
            })
        } {
            if (rootThread.exceptionHandler == exceptionHandler) {
                rootThread.exceptionHandler = exceptionHandler = nil;
            }
        }
    }
    
    init {
        actions = IdentitySet();
        lines = LinkedList(maxLength);
        formatter = splitLines.if({ splitLineFormatter }, { defaultFormatter });
        logToFileAction = {
            |item|
            this.logFile << item[\formatted];
            this.logFile << "\n";
            this.logFile.flush();
        };
        this.level = \info;
    }
    
    logFile {
        ^(logFile ?? { this.class.globalLogFile })
    }
    
    logFile_{
        |path|
        logFile !? { logFile.close() };
        logFile = File.open(path, "a");
    }
    
    logToFile {
        |bool=true|
        if (bool) {
            this.logFile << (
                "\n-------- STARTING Log(%) - % -"
                    .format(
                        "\\" ++ this.name,
                        Date.getDate().asString
                    )
                    .padRight(192, "-") ++ "\n\n"
            );
            
            ShutDown.add({
                this.logFile << (
                    "\n-------- ENDING Log(%) - % -"
                        .format(
                            "\\" ++ this.name,
                            Date.getDate().asString
                        )
                        .padRight(192, "-") ++ "\n\n"
                );
                
            });
            
            actions.add(logToFileAction);
        } {
            
            actions.remove(logToFileAction)
        }
    }
    
    splitLines_{
        |value|
        splitLines = value;
        if (splitLines and: { formatter == defaultFormatter }) {
            formatter = splitLineFormatter
        };
        if (splitLines.not and: { formatter == splitLineFormatter }) {
            formatter = defaultFormatter;
        }
    }
    
    level_{
        |inLevel|
        levels[inLevel] !? {
            |num|
            level = inLevel;
            levelNum = num
        } ?? {
            "Can't find a log level called '%' (levels are: %)".format(
                inLevel,
                levels.keys.collect("'%'".format(_)).join(", ")
            ).warn
        }
    }
    
    addEntry {
        | item |
        lines.add(item);
        if (lines.size() > maxLength) {
            lines.popFirst();
        }
    }
    
    debug {
        | str ...items |
        this.set(str.asString.format(*items), \debug)
    }
    
    info {
        | str ...items |
        this.set(str.asString.format(*items), \info)
    }
    
    warning {
        | str ...items |
        this.set(str.asString.format(*items), \warning)
    }
    
    error {
        | str ...items |
        this.set(str.asString.format(*items), \error)
    }
    
    critical {
        | str ...items |
        this.set(str.asString.format(*items), \critical)
    }
    
    log {
        | str, level |
        this.set(str, level)
    }
    
    set {
        | str, inLevel = \info |
        var logLevel, logItem;
        logLevel = levels[inLevel] ? 0;
        if (logLevel >= levelNum) {
            logItem = (
                \string: str,
                \level: inLevel,
                \time: Date.getDate()
            );
            logItem[\formatted] = this.format(logItem);
            
            this.addEntry(logItem);
            
            if (shouldPost) {
                logItem[\formatted].postln;
            };
            
            actions.do({
                | action |
                action.value(logItem, this);
            });
        }
    }
    
    format {
        | item |
        ^formatter.value(item, this);
    }
}

LogWindow : Singleton {
    var <action, <window, <textView, <names, <logs, textViewSize = 0, connected = false,
        <>font, <>boldFont, <>regularColor, <>errorColor, backgroundColor, pending, lastFlush = 0;
    
    init {
        logs = IdentitySet();
        names = IdentitySet();
        font = Font("Mplus Code 60", 11).weight_(10);
        boldFont = Font("Mplus Code 60", 11).weight_(70);
        regularColor = Color.grey(0.3);
        errorColor = Color.red(0.8);
        backgroundColor = Color(0.85, 0.9, 0.85, 0.7);
        pending = List(size:200);
        
        action = {
            | item, log |
            pending.add([item, log]);
            { this.writeLater() }.defer(0.01);
        };
    }
    
    style_{
        |description|
        font = description[\font] ?? { font };
        boldFont = description[\boldFont] ?? { boldFont };
        regularColor = description[\regularColor] ?? { regularColor };
        errorColor = description[\errorColor] ?? { errorColor };
        this.fontSize_(description[\fontSize] ?? { font.size });
        this.background = description[\background] ?? { backgroundColor };
    }
    
    fontSize_{
        |size|
        font = font.size_(size);
        boldFont = boldFont.size_(size);
    }
    
    writeLater {
        var time = AppClock.seconds;
        if ((time - lastFlush) < 0.25) {
            { this.writeLater() }.defer(time - lastFlush + 0.25);
        } {
            lastFlush = time;
            this.doWrite();
        }
    }
    
    doWrite {
        var item, log, logString, logStringSize;
        var weight;
        
        if (textView.notNil) {
            if (textView.isClosed.not) {
                pending.do {
                    | p |
                    #item, log = p;
                    if (item.notNil) {
                        weight = (10 * Log.levels[item.level].clip(0, 10)).round;
                        logString = item[\formatted] + "\n";
                        logStringSize = logString.size();
                        textView.setStringColor(regularColor, textViewSize-1, 1);
                        textView.setString(logString, 999999999, 0);
                        textView.setFont(font.weight_(weight), textViewSize, logStringSize - 1);
                        if ((log.name == \error) || (Log.levels[item.level] >= Log.levels[\error])) {
                            textView.setStringColor(errorColor, textViewSize, logStringSize - 1)
                        };
                        
                        textViewSize = logStringSize + textViewSize;
                    }
                };
                
                if (pending.isEmpty.not) {
                    pending.clear();
                    textView.select(textViewSize, 0);
                }
            }
        }
    }
    
    set {
        | namesArray |
        var newNames;
        
        namesArray = namesArray ?? [name];
        
        if (namesArray.isKindOf(Symbol) || namesArray.isKindOf(String)) {
            namesArray = [ namesArray ];
        };
        
        newNames = IdentitySet.newFrom(namesArray);
        
        if (connected) {
            // added
            (newNames.difference(names)).do {
                |name|
                this.initForLog(Log(name));
            };
            
            // removed
            (names.difference(newNames)).do {
                |name|
                this.deinitForLog(Log(name));
            }
        };
        
        names = newNames;
        logs = IdentitySet.newFrom(names.collect(Log(_)))
    }
    
    disconnect {
        if (connected) {
            connected = false;
            logs.do({
                | log |
                log.actions.remove(action);
            });
        }
    }
    
    connect {
        if (connected.not) {
            connected = true;
            if (window.notNil) {
                if (window.isClosed.not) {
                    logs.do(this.initForLog(_));
                }
            }
        }
    }
    
    initForLog {
        | log |
        log.actions.add(action);
    }
    
    deinitForLog {
        | log |
        log.actions.remove(action);
    }
    
    update {
        action.value();
    }
    
    clear {
        if (window.notNil) {
            textView.string = "\n";
            textViewSize = 1;
        }
    }
    
    close {
        if (window.notNil) {
            window.close();
        }
    }
    
    background_{
        |color|
        backgroundColor = color;
        window !? {
            textView.background = color;
        }
    }
    
    view {
        if (window.notNil and: { window.isClosed }) { window = nil };
        if (window.isNil) {
            {
                window = View().name_(this.name.asString);
                textView = TextView()
                    .autohidesScrollers_(true)
                    .editable_(false)
                    .background_(backgroundColor)
                    .font_(font);
                
                textView.string_("\n");
                textView.setStringColor(regularColor, 0, 1);
                
                window.recallPosition(\LogWindow, name);
                window.autoRememberPosition(\LogWindow, name);
                
                window.layout_(VLayout(textView).margins_(5).spacing_(0));
                
                CmdPeriod.add(this);
                
                this.connect();
                window.onClose_({
                    this.disconnect();
                    window = nil;
                    textView = nil;
                    CmdPeriod.remove(this);
                    textViewSize = 0;
                });
            }.defer();
        };
        
        this.update();
        
        ^window;
    }
    
    front {
        this.view.front;
    }
    
    cmdPeriod {
        this.clear();
    }
}
