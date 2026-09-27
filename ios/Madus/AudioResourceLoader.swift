import AVFoundation
import UniformTypeIdentifiers

// Uses the public resource-loader API to attach the CDN Referer header.
// All delegate state lives on one serial queue; audio bytes are streamed, never accumulated.
final class AudioResourceLoader: NSObject, AVAssetResourceLoaderDelegate {
    private let source: StreamSource
    private let queue = DispatchQueue(label: "com.madus.audio-loader")
    private var transfers: [ObjectIdentifier: AudioTransfer] = [:]

    init(source: StreamSource) { self.source = source }

    func makeAsset() -> AVURLAsset {
        let suffix = source.contentType == "video/mp4" ? "mp4" : "m4a"
        let asset = AVURLAsset(url: URL(string: "madus-audio://stream/\(UUID().uuidString).\(suffix)")!)
        asset.resourceLoader.setDelegate(self, queue: queue)
        return asset
    }

    func resourceLoader(_ resourceLoader: AVAssetResourceLoader, shouldWaitForLoadingOfRequestedResource request: AVAssetResourceLoadingRequest) -> Bool {
        let id = ObjectIdentifier(request)
        let transfer = AudioTransfer(source: source, loading: request, queue: queue) { [weak self] in
            self?.transfers.removeValue(forKey: id)
        }
        transfers[id] = transfer
        transfer.start()
        return true
    }

    func resourceLoader(_ resourceLoader: AVAssetResourceLoader, didCancel request: AVAssetResourceLoadingRequest) {
        transfers.removeValue(forKey: ObjectIdentifier(request))?.cancel()
    }

    func stop() {
        queue.async { [self] in
            let pending = Array(transfers.values)
            transfers.removeAll()
            pending.forEach { $0.cancel() }
        }
    }
}

private final class AudioTransfer: NSObject, URLSessionDataDelegate {
    private let source: StreamSource
    private let loading: AVAssetResourceLoadingRequest
    private let callbackQueue: DispatchQueue
    private let onFinish: () -> Void
    private var session: URLSession?
    private var finished = false
    private var offset: Int64 = 0
    private var end: Int64 = 0
    private var startOffset: Int64 {
        guard let data = loading.dataRequest else { return 0 }
        return max(data.requestedOffset, data.currentOffset)
    }

    init(source: StreamSource, loading: AVAssetResourceLoadingRequest, queue: DispatchQueue, onFinish: @escaping () -> Void) {
        self.source = source; self.loading = loading; callbackQueue = queue; self.onFinish = onFinish
    }

    func start() {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 24 * 60 * 60
        config.httpShouldSetCookies = false
        config.urlCache = nil
        let delegateQueue = OperationQueue()
        delegateQueue.maxConcurrentOperationCount = 1
        delegateQueue.underlyingQueue = callbackQueue
        let session = URLSession(configuration: config, delegate: self, delegateQueue: delegateQueue)
        self.session = session
        var request = URLRequest(url: source.url)
        request.setValue(BilibiliClient.userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue(source.referer.absoluteString, forHTTPHeaderField: "Referer")
        request.setValue("identity", forHTTPHeaderField: "Accept-Encoding")
        if let data = loading.dataRequest {
            let last = data.requestedOffset + Int64(data.requestedLength) - 1
            request.setValue(data.requestsAllDataToEndOfResource ? "bytes=\(startOffset)-" : "bytes=\(startOffset)-\(max(startOffset, last))", forHTTPHeaderField: "Range")
        } else { request.setValue("bytes=0-1", forHTTPHeaderField: "Range") }
        session.dataTask(with: request).resume()
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) {
        guard request.url?.scheme == "https" else {
            completionHandler(nil)
            finish(MadusError.message("音频跳转地址不支持 HTTPS。"))
            return
        }
        var redirected = request
        redirected.setValue(source.referer.absoluteString, forHTTPHeaderField: "Referer")
        redirected.setValue(BilibiliClient.userAgent, forHTTPHeaderField: "User-Agent")
        redirected.setValue(task.originalRequest?.value(forHTTPHeaderField: "Range"), forHTTPHeaderField: "Range")
        completionHandler(redirected)
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive response: URLResponse, completionHandler: @escaping (URLSession.ResponseDisposition) -> Void) {
        guard !finished, let response = response as? HTTPURLResponse, [200, 206].contains(response.statusCode) else {
            completionHandler(.cancel)
            finish(MadusError.message("音频地址失效或暂不可用，请重新播放。"))
            return
        }
        let range = response.value(forHTTPHeaderField: "Content-Range").flatMap(HTTPByteRange.init)
        let total = range?.total ?? response.expectedContentLength
        guard total > 0, response.statusCode != 206 || range != nil, (range?.start ?? 0) <= startOffset else {
            completionHandler(.cancel)
            finish(MadusError.message("音频服务器返回了无效的分段信息。"))
            return
        }
        offset = range?.start ?? 0
        if let info = loading.contentInformationRequest {
            info.contentType = UTType(mimeType: source.contentType)?.identifier ?? UTType.mpeg4Audio.identifier
            info.contentLength = total
            info.isByteRangeAccessSupported = response.statusCode == 206 || response.value(forHTTPHeaderField: "Accept-Ranges") == "bytes"
        }
        guard let data = loading.dataRequest else {
            completionHandler(.cancel)
            finish(nil)
            return
        }
        end = data.requestsAllDataToEndOfResource ? total : min(total, data.requestedOffset + Int64(data.requestedLength))
        if startOffset >= end {
            completionHandler(.cancel)
            finish(nil)
        } else { completionHandler(.allow) }
    }

    func urlSession(_ session: URLSession, dataTask: URLSessionDataTask, didReceive data: Data) {
        guard !finished, let request = loading.dataRequest else { return }
        let needed = max(request.requestedOffset, request.currentOffset)
        let skip = min(Int64(data.count), max(0, needed - offset))
        let take = min(Int64(data.count) - skip, max(0, end - max(offset, needed)))
        if take > 0 {
            request.respond(with: data.subdata(in: Int(skip)..<Int(skip + take)))
        }
        offset += Int64(data.count)
        if request.currentOffset >= end { finish(nil) }
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        guard !finished else { return }
        if let error { finish(error) }
        else if let data = loading.dataRequest, data.currentOffset < end {
            finish(MadusError.message("音频连接提前中断，请重试。"))
        } else { finish(nil) }
    }

    func cancel() {
        guard !finished else { return }
        finished = true
        session?.invalidateAndCancel()
        session = nil
    }

    private func finish(_ error: Error?) {
        guard !finished else { return }
        finished = true
        if !loading.isCancelled {
            if let error { loading.finishLoading(with: error) } else { loading.finishLoading() }
        }
        session?.invalidateAndCancel()
        session = nil
        onFinish()
    }
}
