import ActivityKit
import SwiftUI
import WidgetKit

@main
struct FoodHomeOrderStatus: Widget {
    private let accent = Color(red: 0.99, green: 0.60, blue: 0.25)
    private let displayName = Bundle.main.object(forInfoDictionaryKey: "CFBundleDisplayName") as? String ?? "Food&Home"

    var body: some WidgetConfiguration {
        ActivityConfiguration(for: LiveOrderAttributes.self) { context in
            HStack(spacing: 14) {
                Image(systemName: context.state.symbol)
                    .font(.title2.weight(.semibold))
                    .foregroundStyle(accent)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 3) {
                    Text(context.isStale ? "\(displayName) · Последний статус" : "\(displayName) · Мой заказ")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text(context.state.title)
                        .font(.headline)
                }
                Spacer(minLength: 4)
            }
            .padding()
            .accessibilityElement(children: .combine)
            .widgetURL(context.attributes.orderURL)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Image(systemName: context.state.symbol).foregroundStyle(accent)
                }
                DynamicIslandExpandedRegion(.center) {
                    Text(displayName).font(.caption).foregroundStyle(.secondary)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    Text(context.state.title).font(.headline)
                }
            } compactLeading: {
                Image(systemName: context.state.symbol).foregroundStyle(accent)
                    .accessibilityLabel(context.state.title)
            } compactTrailing: {
                Text(context.state.compactTitle).font(.caption2.bold()).lineLimit(1).minimumScaleFactor(0.75)
            } minimal: {
                Image(systemName: context.state.symbol).foregroundStyle(accent)
                    .accessibilityLabel(context.state.title)
            }
            .widgetURL(context.attributes.orderURL)
            .keylineTint(accent)
        }
    }
}
