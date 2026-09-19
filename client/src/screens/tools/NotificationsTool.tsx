import { Screen, T, Card, Button, Empty } from "../../components/ui";
import { useApp } from "../../store/AppProvider";
import { notifications } from "../../store/selectors";
export function NotificationsTool() {
  const { state, dispatch } = useApp(),
    items = notifications(state);
  return (
    <Screen>
      <T size={25} bold style={{ marginBottom: 20 }}>
        Your money reminders
      </T>
      {items.map((n) => (
        <Card key={n.id}>
          <T bold={!n.read}>
            {n.icon} {n.title}
          </T>
          <T muted size={12} style={{ marginTop: 7 }}>
            {n.body}
          </T>
        </Card>
      ))}
      {items.length ? (
        <Button
          title="Mark all as read"
          variant="secondary"
          onPress={() =>
            dispatch({
              type: "READ_NOTIFICATIONS",
              ids: items.map((i) => i.id),
            })
          }
        />
      ) : (
        <Empty
          title="You’re all caught up"
          body="Upcoming bills and budget alerts will appear here."
        />
      )}
      <T muted size={12} style={{ marginTop: 20 }}>
        These reminders are calculated when you open the app. Background push
        notifications are not enabled.
      </T>
    </Screen>
  );
}
