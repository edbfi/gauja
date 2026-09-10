// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import XCTest

nonisolated final class ServerCheckTests: XCTestCase {
    @MainActor func testAddressValidation() {
        let app = XCUIApplication()
        app.launch()
        let address = app.textFields["server-address"]
        let button = app.buttons["Check server"]
        let error = app.staticTexts["server-error"]
        let expectedError = "Enter a valid HTTP or HTTPS address without credentials, query or fragment."
        let entered = XCTContext.runActivity(named: "Enter and verify the invalid address") { _ in
            guard address.waitForExistence(timeout: 10), address.isHittable else {
                attachState(app, address: address, button: button, error: error)
                XCTFail("Address field is not actionable")
                return false
            }
            address.tap()
            address.typeText("ftp://invalid")
            let typed = XCTNSPredicateExpectation(
                predicate: NSPredicate(format: "value == %@", "ftp://invalid"), object: address)
            // Allow the same setup window as field discovery: iPad AX snapshots can lag typing.
            // The separate three-second validation deadline below stays unchanged.
            guard XCTWaiter.wait(for: [typed], timeout: 10) == .completed else {
                attachState(app, address: address, button: button, error: error)
                XCTFail("Invalid address was not entered exactly")
                return false
            }
            return true
        }
        guard entered else { return }
        let submitted = XCTContext.runActivity(named: "Verify and submit Check server") { _ in
            guard button.exists, button.isEnabled, button.isHittable else {
                attachState(app, address: address, button: button, error: error)
                XCTFail("Check server is not actionable")
                return false
            }
            button.tap()
            return true
        }
        guard submitted else { return }
        XCTContext.runActivity(named: "Observe validation with the existing three-second wait") { activity in
            let start = ProcessInfo.processInfo.systemUptime
            let appeared = error.waitForExistence(timeout: 3)
            let observed = ProcessInfo.processInfo.systemUptime
            if !appeared {
                // Late observation is diagnostic only; this test must still fail.
                XCTFail("Validation error did not appear within the original observation window")
                attachState(app, address: address, button: button, error: error)
                let diagnosticStart = ProcessInfo.processInfo.systemUptime
                let appearedLater = error.waitForExistence(timeout: 7)
                let detail =
                    "initialStart=\(start), initialEnd=\(observed), diagnosticStart=\(diagnosticStart), "
                    + "diagnosticEnd=\(ProcessInfo.processInfo.systemUptime), appearedLater=\(appearedLater)"
                let timing = XCTAttachment(string: detail)
                timing.name = "Validation observation timing"
                timing.lifetime = .keepAlways
                activity.add(timing)
                attachState(app, address: address, button: button, error: error)
                return
            }
            XCTAssertEqual(error.label, expectedError)
            XCTAssertEqual(address.value as? String, "ftp://invalid")
        }
    }

    @MainActor private func attachState(
        _ app: XCUIApplication, address: XCUIElement, button: XCUIElement, error: XCUIElement
    ) {
        XCTContext.runActivity(named: "Capture validation interaction state") { activity in
            let screenshot = XCTAttachment(screenshot: app.screenshot())
            screenshot.name = "Server validation screenshot"
            screenshot.lifetime = .keepAlways
            activity.add(screenshot)
            let hierarchy = XCTAttachment(string: app.debugDescription)
            hierarchy.name = "Accessibility hierarchy"
            hierarchy.lifetime = .keepAlways
            activity.add(hierarchy)
            let fieldValue = address.exists ? String(describing: address.value) : "unavailable"
            let buttonState = button.exists ? "enabled=\(button.isEnabled), hittable=\(button.isHittable)" : "absent"
            let errorState =
                error.exists
                ? "type=\(error.elementType.rawValue), identifier=\(error.identifier), label=\(error.label)" : "absent"
            let description =
                "uptime=\(ProcessInfo.processInfo.systemUptime), field=\(fieldValue), "
                + "keyboard=\(app.keyboards.count), button=\(buttonState), error=\(errorState)"
            let state = XCTAttachment(string: description)
            state.name = "Input, submission and error state"
            state.lifetime = .keepAlways
            activity.add(state)
        }
    }
}
