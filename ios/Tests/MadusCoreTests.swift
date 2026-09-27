import XCTest
@testable import MadusCore

final class MadusCoreTests: XCTestCase {
    func testBvidFromSharedTextRejectsPartialIdentifiers() {
        XCTAssertEqual(BilibiliParser.bvid(in: "分享 https://www.bilibili.com/video/BV1xx411c7mu/?p=2"), "BV1xx411c7mu")
        XCTAssertNil(BilibiliParser.bvid(in: "BV1xx411c7muEXTRA"))
        XCTAssertNil(BilibiliParser.bvid(in: "BV123"))
    }

    func testDurationsUseSecondsAndDoNotMistakeLongSongsForMilliseconds() {
        XCTAssertEqual(BilibiliParser.duration("1:02:03"), 3723)
        XCTAssertEqual(BilibiliParser.duration("125:30"), 7530)
        XCTAssertEqual(BilibiliParser.duration(10800), 10800)
        XCTAssertEqual(BilibiliParser.duration("nan"), 0)
        XCTAssertEqual(BilibiliParser.duration("1:bad"), 0)
    }

    func testSearchNormalizesHighlightedTitlesAndDeduplicates() {
        let row: [String: Any] = ["bvid": "BV1xx411c7mu", "title": "<em>音乐</em> &amp; 风", "author": "UP", "pic": "//i0.hdslb.com/cover.jpg", "duration": "03:20"]
        let tracks = BilibiliParser.searchTracks([row, row, ["title": "广告"]])
        XCTAssertEqual(tracks.count, 1)
        XCTAssertEqual(tracks.first?.title, "音乐 & 风")
        XCTAssertEqual(tracks.first?.cover?.scheme, "https")
        XCTAssertEqual(tracks.first?.duration, 200)
    }

    func testViewExpandsPartsWithStableDistinctIds() {
        let tracks = BilibiliParser.viewTracks([
            "bvid": "BV1xx411c7mu", "title": "合集", "owner": ["name": "UP"],
            "pages": [["cid": 11, "part": "第一首", "duration": 200], ["cid": 12, "part": "第二首", "duration": 180]]
        ])
        XCTAssertEqual(tracks.map(\.id), ["BV1xx411c7mu_11", "BV1xx411c7mu_12"])
        XCTAssertEqual(tracks.map(\.title), ["第一首", "第二首"])
    }

    func testStreamChoosesAACInsteadOfHigherBitrateUnsupportedCodec() {
        let result = BilibiliParser.stream(["dash": ["audio": [
            ["codecs": "fLaC", "bandwidth": 1000000, "baseUrl": "https://example.org/flac.m4s"],
            ["codecs": "mp4a.40.2", "bandwidth": 192000, "base_url": "https://example.org/aac.m4s"],
            ["codecs": "mp4a.40.2", "bandwidth": 64000, "baseUrl": "https://example.org/low.m4s"]
        ]]], bvid: "BV1xx411c7mu")
        XCTAssertEqual(result?.url.lastPathComponent, "aac.m4s")
        XCTAssertEqual(result?.contentType, "audio/mp4")
    }

    func testStreamNeverSilentlyTruncatesSegmentedVideo() {
        let result = BilibiliParser.stream(["durl": [
            ["url": "https://example.org/one.mp4"], ["url": "https://example.org/two.mp4"]
        ]], bvid: "BV1xx411c7mu")
        XCTAssertNil(result)
        XCTAssertNil(BilibiliParser.stream(["durl": [["url": "https://example.org/video.flv"]]], bvid: "BV1xx411c7mu"))
    }

    func testRangeDistinguishesPartialLengthFromTotalFileLength() {
        let range = HTTPByteRange("bytes 100-199/3000")
        XCTAssertEqual(range?.start, 100)
        XCTAssertEqual(range?.end, 199)
        XCTAssertEqual(range?.total, 3000)
        XCTAssertNil(HTTPByteRange("bytes 100-3000/3000"))
        XCTAssertNil(HTTPByteRange("bytes */3000"))
        XCTAssertNil(HTTPByteRange("bytes 9-1/20"))
    }

    func testQueueEndAndManualSkipInSingleRepeatMode() {
        XCTAssertNil(QueuePolicy.next(count: 0, index: 0, mode: .all, automatic: true))
        XCTAssertNil(QueuePolicy.next(count: 3, index: 2, mode: .off, automatic: true))
        XCTAssertEqual(QueuePolicy.next(count: 3, index: 2, mode: .all, automatic: true), 0)
        XCTAssertEqual(QueuePolicy.next(count: 3, index: 1, mode: .one, automatic: true), 1)
        XCTAssertEqual(QueuePolicy.next(count: 3, index: 1, mode: .one, automatic: false), 2)
    }

    func testWbiSignsEncodedQueryWithFixedClock() {
        let signed = BilibiliParser.signedQuery(["foo": "114", "bar": "514", "baz": "1919810"],
            image: "7cd084941338484aae1ad9425b84077c", sub: "4932caff0ff746eab6f01bf08b70ac45", timestamp: 1702204169)
        XCTAssertEqual(signed, "bar=514&baz=1919810&foo=114&wts=1702204169&w_rid=6149fdadf571698ca7e6a567265cd0ee")
        let chinese = BilibiliParser.signedQuery(["keyword": "音乐 !()"], image: String(repeating: "a", count: 32), sub: String(repeating: "b", count: 32), timestamp: 1)
        XCTAssertTrue(chinese.hasPrefix("keyword=%E9%9F%B3%E4%B9%90%20&wts=1&w_rid="))
    }
}
