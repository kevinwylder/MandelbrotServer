const http = require('http');
const exec = require("child_process").exec;
const sudo = require('sudo');
const formidable = require("formidable");
const fs = require('fs');

var child = sudo(["./m.out"]);
child.stdin.setEncoding('utf-8');
var stack = [];
var processing = false;

function notFound(response) {
    response.writeHead(404, {"Content-Type": "text/html"});
    response.end("404 error");
}

function handleMandelbrot(term, res) {
    res.writeHead(200, {'Content-Type': 'image/png' });
    console.log(term);
    stack.push([term, res, Math.random()]);
    if (!processing) {
        processMandelbrot(stack.pop());
    }
}

function processMandelbrot(frame) {
    processing = true;

    // setup output capture
    var bytesReceived = 0;
    var totalBytes = 5 * 1024 * 1024; // 5 MB upper bound
    child.stdout.on('data', (data) => {
        var size = Buffer.byteLength(data);
        bytesReceived += size;
        frame[1].write(data);

        if (bytesReceived > totalBytes) {
            console.log("Oh fuck over by " + (bytesReceived - totalBytes));
            frame[1].end();
        } else if (bytesReceived == totalBytes) {
            child.stdout.removeAllListeners('data');
            frame[1].end();
        }
    });

    // listen for final size
    child.stderr.on('data', (data) => {
        totalBytes = data.readUIntLE(0, 4);
        if (bytesReceived > totalBytes) {
            console.log("Oh fuck over by " + (bytesReceived - totalBytes));
            frame[1].end();
        } else if (bytesReceived == totalBytes) {
            child.stdout.removeAllListeners('data');
            frame[1].end();
        }

        child.stderr.removeAllListeners('data');
        console.log("stck size " + stack.length);

        // recursion to keep the work "thread" alive
        if (stack.length > 0) {
            var nextFrame = stack.pop();
            processMandelbrot(nextFrame);
        } else {
            processing = false;
        }
    });

    // begin computation
    child.stdin.write(frame[0] + "@");

}


const server = http.createServer((request, response) => {
	if (request.url == "/id") {
		response.writeHead(200, {"Content-Type": "text/html"});
		response.end("kevin-desktop");
    } else if (request.url == "/upload") {
        response.writeHead(200, {'Content-Type': 'text/html'});
        response.write('<form action="fileupload" method="post" enctype="multipart/form-data">');
        response.write('<input type="file" name="filetoupload"><br>');
        response.write('<input type="submit">');
        response.write('</form>');
        return response.end();
    } else if (request.url == "/fileupload") {
        var form = new formidable.IncomingForm();
        form.parse(request, (err, fields, files) => {
            response.write("uploading...</br>");
            fs.rename(files.filetoupload.path, "/home/kevin/Desktop/uploads/" + files.filetoupload.name, (err) => {
                console.log(err);
                response.write("error");
                response.end();
            });
        });
    } else if (request.url.indexOf("fractal") != -1) {
        var term = request.url.split("/").pop();
        if (term.length == 0) {
            notFound(response);
            return;
        } else if (term.match(/^[ma][1-9]*$/)) {
            handleMandelbrot(term, response);
            return;
        } else {
            notFound(response);
            return;
        }
	} else {
        notFound(response);
        return;
    }
});
server.listen(80);


