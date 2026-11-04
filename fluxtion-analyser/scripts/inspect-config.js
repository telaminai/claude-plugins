#!/usr/bin/osascript -l JavaScript
// Read only the selected Claude JSON file; never return its values or credentials.
ObjC.import('Foundation');
function run(argv) {
    if (argv.length !== 1) return 'unreadable';
    var path = argv[0];
    if (!$.NSFileManager.defaultManager.fileExistsAtPath(path)) return 'absent';
    try {
        var text = $.NSString.stringWithContentsOfFileEncodingError(path, $.NSUTF8StringEncoding, null);
        if (!text) return 'unreadable';
        var config = JSON.parse(ObjC.unwrap(text));
        if (!config || typeof config !== 'object' || Array.isArray(config)) return 'unreadable';
        var servers = config.mcpServers;
        if (servers == null) return 'absent';
        if (typeof servers !== 'object' || Array.isArray(servers)) return 'unreadable';
        return Object.keys(servers).indexOf('fluxtion-analyser') >= 0 ? 'present' : 'absent';
    } catch (_) { return 'unreadable'; }
}
